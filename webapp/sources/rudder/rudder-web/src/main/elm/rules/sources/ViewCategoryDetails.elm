module ViewCategoryDetails exposing (..)

import DataTypes exposing (..)
import Html exposing (Html, button, div, i, span, text, h1, h4, ul, li, input, a, p, form, label, textarea, select, option, table, thead, tbody, tr, th, td, small)
import Html.Attributes exposing (id, class, type_, placeholder, value, for, href, colspan, rowspan, style, selected, disabled, attribute)
import Html.Events exposing (onClick, onInput)
import List.Extra
import List
import String exposing ( fromFloat)
import NaturalOrdering exposing (compareOn)
import ApiCalls exposing (..)
import ViewTabContent exposing (buildListCategories)

--
-- This file contains all methods to display the details of the selected category.
--

editionTemplateCat : Model -> EditCategoryDetails -> Bool -> Html Msg
editionTemplateCat model details isNewCat =
  let
    originCategoryName = case details.originCategory of
      Just c  -> c.name
      Nothing -> ""
    category  = details.category

    categoryTitle = if isNewCat then
        span[style "opacity" "0.4"][text "New category"]
      else
         text originCategoryName

    categoryForm =
      if model.ui.hasWriteRights == True then
        form[class "col-xs-12 col-sm-6 col-lg-7"]
        [ div [class "form-group"]
          [ label[for "category-name"][text "Name"]
          , div[]
            [ input[ id "category-name", type_ "text", value category.name, class "form-control" , onInput (\s -> UpdateCategory {category | name = s} ) ][] ]
          ]
        , div [class "form-group"]
          [ label[for "category-parent"][text "Parent"]
          , div[]
            [ select[ id "category-parent", class "form-control" ] --, onInput (\s -> UpdateCategory {category | parent = s} ) ]
              (buildListCategories  "" model.rulesTree)
            ]
          ]
        , div [class "form-group"]
          [ label[for "category-description"][text "Description"]
          , div[]
            [ textarea[ id "category-description", value category.description, placeholder "There is no description", class "form-control" , onInput (\s -> UpdateCategory {category | description = s} ) ][] ]
          ]
        ]
      else
        form[class "col-xs-12 col-sm-6 col-lg-7 readonly-form"]
        [ div [class "form-group"]
          [ label[for "category-name"][text "Name"]
          , div[][text category.name]
          ]
        , div [class "form-group"]
          [ label[for "category-description"][text "Description"]
          , div[]
            ( if String.isEmpty category.description then
                [ span[class "half-opacity"][text "There is no description"] ]
              else
                [ text category.description ]
            )
          ]
        ]

  in
    div [class "main-container"]
    [ div [class "main-header "]
      [ div [class "header-title"]
        [ h1[]
          [ i [class "title-icon fa fa-folder"][]
          , categoryTitle
          ]
        , div[class "header-buttons"]
          ( button [class "btn btn-default", type_ "button", onClick CloseDetails]
            [ text "Close", i [ class "fa fa-times"][]]
          :: ( if model.ui.hasWriteRights == True then
              [ div [ class "btn-group" ]
                [ button [ class "btn btn-danger" , onClick (OpenDeletionPopupCat category)]
                  [ text "Delete", i [ class "fa fa-times-circle"][]]
                ]
              , button [class "btn btn-success", type_ "button", onClick (CallApi (saveCategoryDetails category isNewCat))]
                [ text "Save", i [ class "fa fa-download"][]]
              ]
            else
              []
            )
          )
        ]
      , div [class "header-description"]
        [ p[][text ""] ]
      ]
    , div [class "main-navbar" ]
      [ ul[class "ui-tabs-nav "]
        [ li[class "ui-tabs-tab ui-tabs-active"]
          [ a[][ text "Information" ]
          ]
        ]
      ]
    , div [class "main-details"]
      [ div[class "row"]
        [ categoryForm
        , div [class "col-xs-12 col-sm-6 col-lg-5"][] -- <== Right column
        ]
      ]
    ]