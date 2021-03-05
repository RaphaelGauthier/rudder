package com.normation.plugins

import better.files.File.root
import com.normation.errors.IOResult

import java.util.Properties

case class PluginSettings(
    url : String
  , username : String
  , password : String
  , proxyUrl : Option[String]
  , proxyUser : Option[String]
  , proxyPassword : Option[String]
)

object PluginSettingsService {


  val pluginConfFile = root / "opt" / "rudder" / "etc" / "rudder-pkg" / "rudder-pkg.conf"

  def readPluginSettings() = {

    val p = new Properties()
    for {
      _ <- IOResult.effect(s"Reading properties from ${pluginConfFile.pathAsString}")(p.load(pluginConfFile.newInputStream))

      url <- IOResult.effect(s"Getting plugin repository url in ${pluginConfFile.pathAsString}")(p.getProperty("url"))
      userName <- IOResult.effect(s"Getting user name for plugin download in ${pluginConfFile.pathAsString}")(p.getProperty("username"))
      pass <- IOResult.effect(s"Getting password for plugin download in ${pluginConfFile.pathAsString}")(p.getProperty("password"))
      proxy <- IOResult.effect(s"Getting proxy for plugin download in ${pluginConfFile.pathAsString}") {
        val res = p.getProperty("proxy_url", "")
        if (res == "") None else Some(res)
      }
      proxy_user <- IOResult.effect(s"Getting proxy for plugin download in ${pluginConfFile.pathAsString}") {
        val res = p.getProperty("proxy_user", "")
        if (res == "") None else Some(res)
      }
      proxy_password <- IOResult.effect(s"Getting proxy for plugin download in ${pluginConfFile.pathAsString}") {
        val res = p.getProperty("proxy_password", "")
        if (res == "") None else Some(res)
      }
    } yield {
      PluginSettings(url, userName, pass, proxy, proxy_user, proxy_password)
    }
  }

  def writePluginSettings(settings : PluginSettings) = {
    IOResult.effect({
      pluginConfFile.write(
        s"""[Rudder]
          |url = ${settings.url}
          |username = ${settings.username}
          |password = ${settings.password}
          |proxy_url = ${settings.proxyUrl.getOrElse("")}
          |proxy_user = ${settings.proxyUser.getOrElse("")}
          |proxy_password = ${settings.proxyPassword.getOrElse("")}
          |""".stripMargin)
    })

  }
}