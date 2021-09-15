module ViewRuleDetails exposing (..)

import DataTypes exposing (..)
import Html exposing (Html, button, div, i, span, text, h1, h4, ul, li, input, a, p, form, label, textarea, select, option, table, thead, tbody, tr, th, td, small)
import Html.Attributes exposing (id, class, type_, placeholder, value, for, href, colspan, rowspan, style, selected, disabled, attribute)
import Html.Events exposing (onClick, onInput)
import List.Extra
import List
import String exposing ( fromFloat)
import NaturalOrdering exposing (compareOn)
import ApiCalls exposing (..)
import ViewTabContent exposing (tabContent)

--
-- This file contains all methods to display the details of the selected rule.
--


editionTemplate : Model -> EditRuleDetails -> Bool -> Html Msg
editionTemplate model details isNewRule =
  let
    originRule = details.originRule
    rule = details.rule
    ruleTitle = if (String.isEmpty originRule.name && isNewRule) then
        span[style "opacity" "0.4"][text "New rule"]
      else
         text originRule.name
    (classDisabled, badgeDisabled) = if originRule.enabled /= True then
        ("item-disabled", span[ class "badge-disabled"][])
      else
        ("", text "")
    topButtons =
      let
        disableWhileCreating = case model.mode of
          EditRule _ -> ""
          _ -> " disabled"
        txtDisabled = if rule.enabled == True then "Disable" else "Enable"
      in
        [ li [] [
            a [ class ("action-success"++disableWhileCreating), onClick (GenerateId (\r -> CloneRule originRule (RuleId r)))] [
              i [ class "fa fa-clone"] []
            , text "Clone"
            ]
          ]
        , li [] [
            a [ class ("action-primary "++disableWhileCreating), onClick (OpenDeactivationPopup rule)] [
              i [ class "fa fa-ban"] []
            , text txtDisabled
            ]
          ]
        , li [class "divider"][]
        , li [] [
            a [ class ("action-danger"++disableWhileCreating), onClick (OpenDeletionPopup rule)] [
              i [ class "fa fa-times-circle"] []
            , text "Delete"
            ]
          ]
        ]

    isNotMember : List a -> a -> Bool
    isNotMember listIds id =
      List.Extra.notMember id listIds

    getDiffList : List a -> List a -> (Int, Int)
    getDiffList listA listB =
      let
        originLength   = List.length listA
        selectedLength = List.length listB
      in
        if selectedLength == originLength then
          let
            diff = List.length (List.Extra.findIndices (isNotMember listB) listA)
          in
            (diff, diff)
        else if selectedLength > originLength then
          let
            diff = List.length (List.Extra.findIndices (isNotMember listB) listA)
            lengthDiff = selectedLength - originLength
          in
            (lengthDiff + diff, diff)
        else
          let
            diff = List.length (List.Extra.findIndices (isNotMember listA) listB)
            lengthDiff = originLength - selectedLength
          in
            (diff, lengthDiff + diff)

    (diffDirectivesPos, diffDirectivesNeg) = getDiffList originRule.directives rule.directives
    nbInclude = case originRule.targets of
      [Composition (Or i) (Or e)] -> List.length i
      targets -> List.length targets
  in
    div [class "main-container"]
    [ div [class "main-header "]
      [ div [class "header-title"]
        [ h1[class classDisabled]
          [ ruleTitle
          , badgeDisabled
          ]
        , div[class "header-buttons"]
          ( button [class "btn btn-default", type_ "button", onClick CloseDetails][text "Close", i [ class "fa fa-times"][]]
          :: (
            if model.ui.hasWriteRights then
              [ div [ class "btn-group" ]
                [ button [ class "btn btn-default dropdown-toggle" , attribute "data-toggle" "dropdown" ] [
                  text "Actions "
                  , i [ class "caret" ] []
                  ]
                , ul [ class "dropdown-menu" ] topButtons
                ]
                , button [class "btn btn-success", type_ "button", onClick (CallApi (saveRuleDetails rule isNewRule))][text "Save", i [ class "fa fa-download"] []]
              ]
            else
              []
            )
          )
        ]
      , div [class "header-description"]
        [ p[][text originRule.shortDescription] ]
      ]
    , div [class "main-navbar" ]
      [ ul[class "ui-tabs-nav "]
        [ li[class ("ui-tabs-tab" ++ (if details.tab == Information   then " ui-tabs-active" else ""))]
          [ a[onClick (ChangeTabFocus Information  )]
            [ text "Information" ]
          ]
        , li[class ("ui-tabs-tab" ++ (if details.tab == Directives    then " ui-tabs-active" else ""))]
          [ a[onClick (ChangeTabFocus Directives   )]
            [ text "Directives"
            , span[class "badge badge-secondary badge-resources tooltip-bs"]
              [ span [class "nb-resources"] [ text (String.fromInt(List.length rule.directives))]
              , ( if diffDirectivesPos /= 0 then span [class "nb-resources new"] [ text (String.fromInt diffDirectivesPos)] else text "")
              , ( if diffDirectivesNeg /= 0 then span [class "nb-resources del"] [ text (String.fromInt diffDirectivesNeg)] else text "")
              ]
            ]
          ]
        , li[class ("ui-tabs-tab" ++ (if details.tab == Groups        then " ui-tabs-active" else ""))]
          [ a[onClick (ChangeTabFocus Groups       )]
            [ text "Groups"
            , span[class "badge badge-secondary badge-resources tooltip-bs"]
              [ span [class "nb-resources"] [ text (String.fromInt(nbInclude))]
              ]
            ]
          ]
        , li[class ("ui-tabs-tab" ++ (if details.tab == TechnicalLogs then " ui-tabs-active" else ""))]
          [ a[onClick (ChangeTabFocus TechnicalLogs)]
            [ text "Technical logs"]
          ]
        ]
      ]
    , div [class "main-details"]
      [ tabContent model details isNewRule ]
    ]