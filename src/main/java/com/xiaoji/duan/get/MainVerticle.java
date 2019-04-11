package com.xiaoji.duan.get;

import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

public class MainVerticle extends AbstractVerticle {

	private ThymeleafTemplateEngine thymeleaf = null;
	private WebClient client = null;
	private MongoClient mongodb = null;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		JsonObject config = new JsonObject();
		config.put("host", "mongodb");
		config.put("port", 27017);
		config.put("keepAlive", true);
		mongodb = MongoClient.createShared(vertx, config);

		thymeleaf = ThymeleafTemplateEngine.create(vertx);
		TemplateHandler templatehandler = TemplateHandler.create(thymeleaf);

		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setSuffix(".html");
		resolver.setCacheable(false);
		resolver.setTemplateMode("HTML5");
		resolver.setCharacterEncoding("utf-8");
		thymeleaf.getThymeleafTemplateEngine().setTemplateResolver(resolver);

		Router router = Router.router(vertx);

		StaticHandler staticfiles = StaticHandler.create().setCachingEnabled(false).setWebRoot("static");
		router.route("/get/static/*").handler(staticfiles);
		router.route("/get").pathRegex("\\/.+\\.json").handler(staticfiles);
		router.route("/get/index").handler(BodyHandler.create());
		router.route("/get/index").handler(this::index);
		router.routeWithRegex("\\/get\\/(?<prefix>[^\\/]{3})\\.js").handler(BodyHandler.create());
		router.routeWithRegex("\\/get\\/(?<prefix>[^\\/]{3})\\.js").handler(ctx -> this.getJavaScript(ctx));
		router.routeWithRegex("\\/get\\/(?<prefix>[^\\/]{3})\\.css").handler(BodyHandler.create());
		router.routeWithRegex("\\/get\\/(?<prefix>[^\\/]{3})\\.css").handler(ctx -> this.getStyleSheet(ctx));

		router.route("/get").pathRegex("\\/[^\\.]*").handler(templatehandler);

		HttpServerOptions option = new HttpServerOptions();
		option.setCompressionSupported(true);

		vertx.createHttpServer(option).requestHandler(router::accept).listen(8080, http -> {
			if (http.succeeded()) {
				startFuture.complete();
				System.out.println("HTTP server started on http://localhost:8080");
			} else {
				startFuture.fail(http.cause());
			}
		});
	}

	private void getStyleSheet(RoutingContext ctx) {
		String prefix = ctx.request().getParam("prefix");

		String host = ctx.request().getHeader("x-forwarded-host");
		if (null == host) {
			host = ctx.request().getHeader("host");
		}
		String group = host.substring(0, host.indexOf('.'));

		JsonObject query = new JsonObject().put("group", group).put("prefix", prefix);

		System.out.println("Query stylesheet for " + group + "/" + prefix + ".css");

		mongodb.findOne("get_group_prefix_stylesheet", query, new JsonObject(), findOne -> {
			if (findOne.succeeded()) {
				JsonObject stored = findOne.result();

				if (stored != null && stored.containsKey("stylesheet")) {
					ctx.response().putHeader("content-type", "text/css; charset=utf-8");
					ctx.response().end(stored.getString("stylesheet"));
				} else {
					ctx.response().putHeader("content-type", "text/css; charset=utf-8");
					ctx.response().end("");
				}
			} else {
				ctx.response().putHeader("content-type", "text/css; charset=utf-8");
				ctx.response().end("");
			}
		});
	}

	private void getJavaScript(RoutingContext ctx) {

		String prefix = ctx.request().getParam("prefix");

		String host = ctx.request().getHeader("x-forwarded-host");
		if (null == host) {
			host = ctx.request().getHeader("host");
		}
		String group = host.substring(0, host.indexOf('.'));

		JsonObject query = new JsonObject().put("group", group).put("prefix", prefix);

		System.out.println("Query javascript for " + group + "/" + prefix + ".js");

		mongodb.findOne("get_group_prefix_javascript", query, new JsonObject(), findOne -> {
			if (findOne.succeeded()) {
				JsonObject stored = findOne.result();

				if (stored != null && stored.containsKey("javascript")) {
					ctx.response().putHeader("content-type", "application/javascript; charset=utf-8");
					ctx.response().end(stored.getString("javascript"));
				} else {
					ctx.response().putHeader("content-type", "application/javascript; charset=utf-8");
					ctx.response().end("");
				}
			} else {
				ctx.response().putHeader("content-type", "application/javascript; charset=utf-8");
				ctx.response().end("");
			}
		});
	}

	private void index(RoutingContext ctx) {
		Cookie ctoken = ctx.getCookie("authorized_user");
		Cookie copenid = ctx.getCookie("authorized_openid");

		String token = ctoken == null ? "" : ctoken.getValue();
		String openid = copenid == null ? "" : copenid.getValue();

		Map<String, String> user = new HashMap();
		user.put("name", "Ï¯Àí¼Ó");

		if (token != null && !"".equals(token) && openid != null && !"".equals(openid)) {
			client.head(8080, "sa-aba", "/aba/user/" + openid + "/info").send(handler -> {
				if (handler.succeeded()) {
					JsonObject result = handler.result().bodyAsJsonObject();

					Map user1 = result.getJsonObject("data").mapTo(HashMap.class);

					user.putAll(user1);

				} else {
					handler.cause().printStackTrace();
				}

				ctx.put("userinfo", user);
				ctx.next();
			});
		} else {
			ctx.put("userinfo", user);
			ctx.next();
		}
	}
}
