port module TechniqueVersion.Init exposing (..)

import TechniqueVersion.DataTypes exposing (..)
import Json.Decode exposing (..)
import Json.Decode.Pipeline exposing (..)

-- PORTS / SUBSCRIPTIONS
port createDirective   : String -> Cmd msg
port errorNotification : String -> Cmd msg
port initTooltips      : String -> Cmd msg
port getTechniquesList : (Value -> msg) -> Sub msg

subscriptions : Model -> Sub Msg
subscriptions model =
  getTechniquesList (GetTechniquesList <<  decodeValue (Json.Decode.list decodeTechnique))

-- init : { contextPath : String, hasWriteRights : Bool, versions : List Technique } -> ( Model, Cmd Msg )
init : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
  let
    initUi      = UI flags.hasWriteRights False
    initModel   = Model flags.contextPath initUi Nothing -- flags.versions
  in
    ( initModel , Cmd.none )

decodeTechnique : Decoder Technique
decodeTechnique =
  succeed Technique
    |> required "version" string
    |> required "isDeprecated" bool
    |> required "deprecationMessage" string
    |> required "acceptationDate" string
    |> required "dscSupport" bool
    |> required "classicSupport" bool
    |> required "multiVersionSupport" string
    |> required "mvsMessage" string