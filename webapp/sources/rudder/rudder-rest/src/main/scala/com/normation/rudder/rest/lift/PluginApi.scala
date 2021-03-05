package com.normation.rudder.rest.lift

import com.normation.plugins.PluginSettings
import com.normation.plugins.PluginSettingsService
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.ApiVersion
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.RestExtractorService
import com.normation.rudder.rest.RestUtils
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.DefaultFormats
import net.liftweb.json.Serialization
import com.normation.rudder.rest.{PluginApi => API}

class PluginApi (
  restExtractorService: RestExtractorService

) extends LiftApiModuleProvider[API] {

  def schemas = API

  def getLiftEndpoints(): List[LiftApiModule] =
    API.endpoints.map(e => e match {
      case API.GetPluginsSettings => GetPluginSettings
      case API.UpdatePluginsSettings => UpdatePluginSettings
    })

  object GetPluginSettings extends LiftApiModule0 {
    val schema  = API.GetPluginsSettings
    val restExtractor = restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      import com.normation.box._
    val json=   for {
        conf <- PluginSettingsService.readPluginSettings()
      } yield {
      import net.liftweb.json.JsonDSL._
      (  ("username" -> conf.username)
      ~  ("password" -> conf.password)
      ~  ("url" -> conf.url)
      ~ ("proxyUrl" -> conf.proxyUrl)
      ~ ("proxyUser" -> conf.proxyUser)
      ~ ("proxyPassword" -> conf.proxyPassword)
      )

      }
      RestUtils.response(
        restExtractor
      , "pluginSettings"
      , None
      )(
        json.toBox
      , req
      , s"Could not get plugin settings"
      ) ("getPluginSettings")
    }

  }

  object UpdatePluginSettings extends LiftApiModule0 {
    val schema = API.UpdatePluginsSettings
    val restExtractor = restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      import com.normation.box._
      import com.normation.errors._

      implicit val formats = DefaultFormats
      val json=
        for {
        json <- req.json.toIO
        conf <- IOResult.effect(Serialization.read[PluginSettings](net.liftweb.json.compactRender(json)))
        _ <- PluginSettingsService.writePluginSettings(conf)

      } yield {
        import net.liftweb.json.JsonDSL._
        (  ("username" -> conf.username)
          ~  ("password" -> conf.password)
          ~  ("url" -> conf.url)
          ~ ("proxyUrl" -> conf.proxyUrl)
          ~ ("proxyUser" -> conf.proxyUser)
          ~ ("proxyPassword" -> conf.proxyPassword)
          )

      }
      RestUtils.response(
        restExtractor
        , "pluginSettings"
        , None
      )(
        json.toBox
        , req
        , s"Could not update plugin settings"
      ) ("updatePluginSettings")
    }
  }

}