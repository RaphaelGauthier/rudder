module Init exposing (..)

import ApiCalls exposing (..)
import DataTypes exposing (..)
import Dict exposing (..)


subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none

init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
      sections : Dict Int Section
      sections =
        Dict.fromList
          [ (0 , Welcome Complete                )
          , (1 , Account Default "" "" )
          , (2 , Metrics Default False False     )
          , (3 , GettingStarted Default          )
          ]

      initModel = Model flags.contextPath sections 0
    in
      ( initModel
      , Cmd.none
      )