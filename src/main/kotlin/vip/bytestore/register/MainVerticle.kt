package vip.bytestore.register

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.properties.PropertyFileAuthentication
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.common.template.TemplateEngine
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.MySQLPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {

    val connectOptions = MySQLConnectOptions()
      .setPort(3306)
      .setHost("117.50.183.227")
      .setDatabase("db_ant")
      .setUser("root")
      .setPassword("!JLUmy441522")

    val poolOptions = PoolOptions()
      .setMaxSize(5)

    val pool = MySQLPool.pool(vertx, connectOptions, poolOptions)

    val router = Router.router(vertx)
    router.route().handler(BodyHandler.create())
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))
    val authn: PropertyFileAuthentication = PropertyFileAuthentication.create(vertx, "auth.properties")
    //登录处理，跳转到首页
    router.route("/session").handler(FormLoginHandler.create(authn).setDirectLoggedInOKURL("/"))
    //私有页面跳转到登录页面
    router.route("/").handler(RedirectAuthHandler.create(authn, "/login.html"))
    router.route("/index.html").handler(RedirectAuthHandler.create(authn, "/login.html"))
    router.route("/cards.html").handler(RedirectAuthHandler.create(authn, "/login.html"))



    router.route("/logout").handler { context: RoutingContext ->
      context.clearUser()
      context.response().putHeader("location", "/login.html").setStatusCode(302).end()
    }

    val engine: TemplateEngine = ThymeleafTemplateEngine.create(vertx)
//    val handler = TemplateHandler.create(engine)


    router.route().handler(StaticHandler.create().setCachingEnabled(false))
//    router.get("/assets").handler(StaticHandler.create().setCachingEnabled(false))



    router.get("/cards.html").handler { ctx ->
      val data = JsonObject().put("welcome", "Hi there!")
      ctx.put("title","hahaha")
      engine.render(data, "templates/cards.html") { res ->
        if (res.succeeded()) {
          ctx.response().end(res.result())
        } else {
          ctx.fail(res.cause())
        }
      }
    }


    router.post("/generate").handler { ctx->
      val now = LocalDateTime.now()
      val number = ctx.request().getParam("number","50").trim().toInt()
      val type = ctx.request().getParam("type","天卡").trim()
      val remark = ctx.request().getParam("remark",now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

      val batch: MutableList<Tuple> = ArrayList()

      for (i in 1..number){
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase()
        batch.add(Tuple.of(uuid,type,"启用","初始",now,null,null,null,remark))
      }

      var t =  pool.withConnection {
        it.preparedQuery("INSERT INTO t_card (code,`type`,`state`,status,create_time,sign_in_time,expire_time,token,remark) VALUES (?,?,?,?,?,?,?,?,?)")
        .executeBatch(batch)
      }

      t.onSuccess {
        println("..........")
        it.forEach { row->
          println(row.getString("code"))
        }
      }
      t.onFailure {
        it.printStackTrace()
      }
      ctx.redirect("/cards.html")
      }

    vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(8888) { http ->
        if (http.succeeded()) {
          startPromise.complete()
        } else {
          startPromise.fail(http.cause())
        }
      }
  }
}
