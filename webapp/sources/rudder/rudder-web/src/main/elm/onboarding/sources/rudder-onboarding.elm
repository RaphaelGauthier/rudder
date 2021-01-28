module Onboarding exposing (update)

import Browser
import DataTypes exposing (..)
import Http exposing (Error)
import Init exposing (init, subscriptions)
import View exposing (view)
import Dict exposing (..)
import Result

main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }

update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of
    ChangeActiveSection index ->
      let
        updateSection : Maybe Section -> Maybe Section
        updateSection section =
          case section of
            Just s  -> case s of
              Account se u p ->
                case se of
                  Default -> Just (Account Visited u p )
                  _       -> Just s
              Metrics se _ _ ->
                case se of
                  Default -> Just (Metrics Visited False False )
                  _       -> Just s
              _ -> Just s

            Nothing -> Nothing

        newSections = model.sections |> Dict.update 3 updateSection

      in
        ({model | activeSection = index, sections = newSections}, Cmd.none)

    GoToLast ->
      ({model | activeSection = (Dict.size model.sections) - 1}, Cmd.none)