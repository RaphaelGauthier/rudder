module ApiCalls exposing (..)

import DataTypes exposing (Model, Msg(..))
import Http exposing (emptyBody, expectJson, jsonBody, request, send)

getUrl: DataTypes.Model -> String -> String
getUrl m url =
  m.contextPath ++ "/secure/api" ++ url