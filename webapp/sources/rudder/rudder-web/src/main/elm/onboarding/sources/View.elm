module View exposing (..)

import DataTypes exposing (..)
import Html exposing (Html, button, div, i, span, text, h1, h3, ul, li, b, label, input, form, a)
import Html.Attributes exposing (class, type_, name, id, href, for)
import Html.Attributes.Autocomplete as Autocomplete
import Html.Attributes.Extra exposing (autocomplete)
import Html.Events exposing (onClick)
import List exposing (any, intersperse, map, sortWith)
import List.Extra exposing (minimumWith)
import String exposing (lines)
import Dict exposing (..)

view : Model -> Html Msg
view model =
  let
    completeClass = "section-complete"
    completeIcon  = "fa fa-check"

    warningClass  = "section-warning"
    warningIcon   = "fas fa-exclamation"

    defaultClass  = "section-default"
    defaultIcon   = "fa fa-info"

    visitedClass  = "section-visited"
    
    sidebarSection : Int -> Section -> List (Html Msg) -> List (Html Msg)
    sidebarSection i s liList =
      let
        titleSection =
          case s of
            Welcome _        -> "Welcome"
            Account _ _ _    -> "Account"
            Metrics _ _ _    -> "Metrics"
            GettingStarted _ -> "Getting Started"

        activeClass = if i == model.activeSection then "activeSection" else ""

        stateClass  = case s of
          Welcome _       -> completeClass
          Account se u p   ->
            case se of
              Visited  -> visitedClass
              Complete -> completeClass
              Warning  -> warningClass
              _        -> defaultClass
          Metrics se tm cm ->
            case se of
              Visited  -> visitedClass
              Complete -> completeClass
              Warning  -> warningClass
              _        -> defaultClass
          _ -> defaultClass

        liItem = li [class (activeClass ++ " " ++ stateClass)]
          [ div [class "timeline-connector"][]
          , div [class "timeline-item", onClick (ChangeActiveSection i)]
            [ span [class "item-dot"][]
            , span [class "item-title"][text titleSection]
            ]
          ]
      in
        liItem :: liList

    summaryList : Int -> Section -> List (Html Msg) -> List (Html Msg)
    summaryList index section liList =
      let
        -- settings = model.settings
        (stateClass, iconClass, textItem) = case section of
          Welcome _       -> (completeClass , completeIcon , "Rudder is correctly installed.")

          Account s u p   ->
            case s of
              Complete -> (completeClass , completeIcon , "Your account "++ u ++ " has been linked to your Rudder!"  )
              Warning  -> (warningClass  , warningIcon  , "There is a probleme with your account credentials."       )
              _        -> (defaultClass  , defaultIcon  , "No account have been linked to your Rudder installation." )

          Metrics s tm cm ->
            case s of
              Complete -> (completeClass , completeIcon , "Tech and Core metrics will be shared anonymously with us, thanks for your help!" )
              Warning  -> (warningClass  , warningIcon  , "" )
              _        -> (defaultClass  , defaultIcon  , "No metrics will be shared." )

          _ -> ("" , "" , "" )

        liItem =
          li[class stateClass]
            [ span[][i[class iconClass][]]
            , text textItem
            ]
      in
        liItem :: liList

    activeSection : List (Html Msg)
    activeSection =
      case (Dict.get model.activeSection model.sections) of
        Just s ->
          case s of
            Welcome _ ->
              [ h3 [] [text "Welcome"]
              , div[]
                [ span[] [text "Rudder installation "]
                , b[ class "text-success" ] [text "is complete"]
                , span[] [text ". Welcome!"]
                ]
              , div[ class "wizard-btn-group"]
                [ button[class "btn btn-default", type_ "button", onClick (GoToLast)] [text "I will configure my Rudder later"]
                , button[class "btn btn-success", type_ "button", onClick (ChangeActiveSection (model.activeSection+1))] [text "Let's configure my account"]
                ]
              ]

            Account _ _ _ ->
              [ h3 [] [text "Account"]
              , div[] [text "Configure your Rudder account for automated plugin download and upgrade."]
              , form[ class "wizard-form", name "wizard-account"]
                [ div [class "form-group"]
                  [ label[] [text "Username"]
                  , input[class "form-control sm-width", type_ "text", name "rudder-username", id "rudder-username"][]
                  ]
                , div [class "form-group"]
                  [ label[] [text "Password"]
                  , input[class "form-control sm-width", type_ "password", name "rudder-password" ][]
                  ]
                , div [class "form-separator"][text "or"]
                , div[ class "wizard-btn-group sm-width text-center"]
                  [ button[class "btn btn-default", type_ "button", onClick (ChangeActiveSection (model.activeSection+1))] [text "Skip, I will create my account later"]
                  ]
                ]
              ]

            Metrics _ _ _ ->
              [ h3 [] [text "Metrics"]
              , div[] [text "Help us improve Rudder by providing anonymous usage metrics."]
              , div[]
                [ span[] [text "We take special care of your security and privacy, "]
                , a [href "#"] [text "read more about it on the site"]
                , span[] [text "."]
                ]
              , form [class "wizard-form"]
                [ div [class "checkbox-cards"] 
                  [ div []
                    [ input[type_ "checkbox", name "core-metrics", id "core-metrics"][]
                    , label[for "core-metrics"]
                      [i [class "core"][]
                      , span [] [text "Core metrics"]
                      ]
                    ]
                  , div []
                    [ input[type_ "checkbox", name "tech-metrics", id "tech-metrics"][]
                    , label[for "tech-metrics"]
                      [i [class "tech"][]
                      , span [] [text "Tech metrics"]
                      ]
                    ]
                  ]
                , div [class "form-separator"][text "or"]
                , div[ class "wizard-btn-group sm-width text-center"]
                  [ button[class "btn btn-default", type_ "button", onClick (ChangeActiveSection (model.activeSection+1))] [text "Skip, I do not want to share metrics for the moment"]
                  ]
                ]
              ]

            GettingStarted _ ->
              [ h3 [] [text "Getting Started"]
              , ul[class "sections-summary"]
                (model.sections
                  |> Dict.remove 3 -- Remove "Getting Started" section from the summary
                  |> Dict.foldr summaryList [])
              , div[]
                [ span[] [text "If you are new to Rudder, we advice you to "]
                , a [href "#"] [text "follow the getting started guide"]
                , span[] [text "."]
                ]
              , div[ class "wizard-btn-group"]
                [ button[class "btn btn-success", type_ "button"] [text "I’m ready!", i[class "fa fa-rocket"][]]
                ]
              ]

        Nothing -> []

  in
    div [class "rudder-template"]
    [ div [class "one-col"]
      [ div [class "one-col-main"]
        [ div [class "template-sidebar sidebar-left"]
          [ div [class "sidebar-body"]
            [ ul[class "wizard-timeline"]
                (Dict.foldr sidebarSection [] model.sections)
            ]
          ]
        , div [class "template-main"]
          [ div [class "main-container"]
            [ div [class "main-details"]
              [ div [class "wizard-section"] activeSection ]
            ]
          ]
        ]
      ]
    ]
