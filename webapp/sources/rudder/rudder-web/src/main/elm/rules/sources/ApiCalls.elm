module ApiCalls exposing (..)

import DataTypes exposing (..)
import Http exposing (..)
import JsonDecoder exposing (..)
import JsonEncoder exposing (..)


--
-- This files contains all API calls for the Rules UI
-- Summary:
-- GET    /rules/tree: get the rules tree
-- GET    /settings/global_policy_mode : Get the global policy mode settings
-- GET    /groups/tree: get the groups tree
-- GET    /directives/tree : get the directives tree
-- GET    /rules/${id} : get the details of the selected rules
-- GET    /compliance/rules?level=6 : get the compliance details of all rules
-- PUT    /rules : Create a new rule (error if existing)
-- POST   /rules/${id} : Update an existing rule (error if it doesn't exist yet)


getUrl: DataTypes.Model -> String -> String
getUrl m url =
  m.contextPath ++ "/secure/api" ++ url

getRulesTree : Model -> Cmd Msg
getRulesTree model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/rules/tree"
        , body            = emptyBody
        , expect          = expectJson decodeGetRulesTree
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetRulesResult req


getPolicyMode : Model -> Cmd Msg
getPolicyMode model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/settings/global_policy_mode"
        , body            = emptyBody
        , expect          = expectJson decodeGetPolicyMode
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetPolicyModeResult req

getGroupsTree : Model -> Cmd Msg
getGroupsTree model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/groups/tree"
        , body            = emptyBody
        , expect          = expectJson decodeGetGroupsTree
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetGroupsTreeResult req

getTechniquesTree : Model -> Cmd Msg
getTechniquesTree model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/directives/tree"
        , body            = emptyBody
        , expect          = expectJson decodeGetTechniquesTree
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetTechniquesTreeResult req

getRuleDetails : Model -> RuleId -> Cmd Msg
getRuleDetails model ruleId =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model ("/rules/" ++ ruleId.value)
        , body            = emptyBody
        , expect          = expectJson decodeGetRuleDetails
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetRuleDetailsResult req

getRulesCategoryDetails : Model -> String -> Cmd Msg
getRulesCategoryDetails model catId =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model ("/rules/categories/" ++ catId)
        , body            = emptyBody
        , expect          = expectJson decodeGetCategoryDetails
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetCategoryDetailsResult req

getRulesCompliance : Model -> Cmd Msg
getRulesCompliance model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/compliance/rules?level=6"
        , body            = emptyBody
        , expect          = expectJson decodeGetRulesCompliance
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetRulesComplianceResult req

getNodesList : Model -> Cmd Msg
getNodesList model =
  let
    req =
      request
        { method          = "GET"
        , headers         = []
        , url             = getUrl model "/nodes"
        , body            = emptyBody
        , expect          = expectJson decodeGetNodesList
        , timeout         = Nothing
        , withCredentials = False
        }
  in
    send GetNodesList req


saveRuleDetails : Rule -> Bool -> Model -> Cmd Msg
saveRuleDetails ruleDetails creation model =
  let
    (method, url) = if creation then ("PUT","/rules") else ("POST", ("/rules/"++ruleDetails.id.value))
    req =
      request
        { method  = method
        , headers = []
        , url     = getUrl model url
        , body    = encodeRuleDetails ruleDetails |> jsonBody
        , expect  = expectJson decodeGetRuleDetails
        , timeout = Nothing
        , withCredentials = False
        }
  in
    send SaveRuleDetails req

saveDisableAction : Rule -> Model ->  Cmd Msg
saveDisableAction ruleDetails model =
  let
    req =
      request
        { method  = "POST"
        , headers = []
        , url     = getUrl model ("/rules/"++ruleDetails.id.value)
        , body    = encodeRuleDetails ruleDetails |> jsonBody
        , expect  = expectJson decodeGetRuleDetails
        , timeout = Nothing
        , withCredentials = False
        }
  in
    send SaveDisableAction req

saveCategoryDetails : (Category Rule) -> Bool -> Model -> Cmd Msg
saveCategoryDetails category creation model =
  let
    (method, url) = if creation then ("PUT","/rules/categories") else ("POST", ("/rules/categories/"++category.id))
    req =
      request
        { method  = method
        , headers = []
        , url     = getUrl model url
        , body    = encodeCategoryDetails category |> jsonBody
        , expect  = expectJson decodeGetCategoryDetails
        , timeout = Nothing
        , withCredentials = False
        }
  in
    send SaveCategoryResult req

deleteRule : Rule -> Model -> Cmd Msg
deleteRule rule model =
  let
    req =
      request
        { method  = "DELETE"
        , headers = []
        , url     = getUrl model "/rules/" ++ rule.id.value
        , body    = emptyBody
        , expect  = expectJson decodeDeleteRuleResponse
        , timeout = Nothing
        , withCredentials = False
        }
  in
    send DeleteRule req

deleteCategory : (Category Rule) -> Model -> Cmd Msg
deleteCategory category model =
  let
    req =
      request
        { method  = "DELETE"
        , headers = []
        , url     = getUrl model "/rules/categories/" ++ category.id
        , body    = emptyBody
        , expect  = expectJson decodeDeleteCategoryResponse
        , timeout = Nothing
        , withCredentials = False
        }
  in
    send DeleteCategory req