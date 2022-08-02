﻿function Technique-By-Rudder {
  [CmdletBinding()]
  param (
      [parameter(Mandatory=$true)]
      [string]$reportId,
      [parameter(Mandatory=$true)]
      [string]$techniqueName,
      [parameter(Mandatory=$true)]
      [string]$TechniqueParameter,
      [Rudder.PolicyMode]$policyMode
  )
  $reportIdBase = $reportId.Substring(0,$reportId.Length-1)
  $localContext = [Rudder.Context]::new()
  $localContext.Merge($system_classes)
  $resources_dir = $PSScriptRoot + "\resources"

  $reportId=$reportIdBase+"id1"
  $componentKey = "$($node.properties[apache_package_name])"
  $reportParams = @{
    ClassPrefix = ([Rudder.Condition]::canonify(("package_install_version_" + $componentKey)))
    ComponentKey = $componentKey
    ComponentName = "Customized component"
    PolicyMode = $policyMode
    ReportId = $reportId
    DisableReporting = $false
    TechniqueName = $techniqueName
  }
  $class = "(debian)"
  if ($localContext.Evaluate($class)) {
    $call = Package-Install-Version -PackageName "$($node.properties[apache_package_name])" -PackageVersion "2.2.11" -PolicyMode $policyMode
    $methodContext = Compute-Method-Call @reportParams -MethodCall $call
    $localContext.merge($methodContext)
  } else {
    Rudder-Report-NA @reportParams
  }

  $reportId=$reportIdBase+"id2"
  $componentKey = "Write-Host `"testing special characters ` è &é 'à é `""
  $reportParams = @{
    ClassPrefix = ([Rudder.Condition]::canonify(("command_execution_" + $componentKey)))
    ComponentKey = $componentKey
    ComponentName = "Command execution"
    PolicyMode = $policyMode
    ReportId = $reportId
    DisableReporting = $true
    TechniqueName = $techniqueName
  }
  $class = "(debian.windows)"
  if ($localContext.Evaluate($class)) {
    $call = Command-Execution -Command "Write-Host `"testing special characters ` è &é 'à é `"" -PolicyMode $policyMode
    $methodContext = Compute-Method-Call @reportParams -MethodCall $call
    $localContext.merge($methodContext)
  } else {
    Rudder-Report-NA @reportParams
  }

  $reportId=$reportIdBase+"id3"
  $componentKey = "$($node.properties[apache_package_name])"
  $reportParams = @{
    ClassPrefix = ([Rudder.Condition]::canonify(("service_start_" + $componentKey)))
    ComponentKey = $componentKey
    ComponentName = "Customized component"
    PolicyMode = $policyMode
    ReportId = $reportId
    DisableReporting = $false
    TechniqueName = $techniqueName
  }
  $class = "package_install_version_" + ([Rudder.Condition]::canonify($componentKey)) + "_repaired"
  if ($localContext.Evaluate($class)) {
    $call = Service-Start -ServiceName "$($node.properties[apache_package_name])" -PolicyMode $policyMode
    $methodContext = Compute-Method-Call @reportParams -MethodCall $call
    $localContext.merge($methodContext)
  } else {
    Rudder-Report-NA @reportParams
  }

  $reportId=$reportIdBase+"id4"
  $componentKey = "openssh-server"
  $reportParams = @{
    ClassPrefix = ([Rudder.Condition]::canonify(("package_install_" + $componentKey)))
    ComponentKey = $componentKey
    ComponentName = "Package install"
    PolicyMode = $policyMode
    ReportId = $reportId
    DisableReporting = $false
    TechniqueName = $techniqueName
  }
  Rudder-Report-NA @reportParams

  $reportId=$reportIdBase+"id5"
  $componentKey = "/bin/echo `"testing special characters ` è &é 'à é `"\"
  $reportParams = @{
    ClassPrefix = ([Rudder.Condition]::canonify(("command_execution_" + $componentKey)))
    ComponentKey = $componentKey
    ComponentName = "Command execution"
    PolicyMode = $policyMode
    ReportId = $reportId
    DisableReporting = $false
    TechniqueName = $techniqueName
  }
  $class = "cfengine-community"
  if ($localContext.Evaluate($class)) {
    $call = Command-Execution -Command "/bin/echo `"testing special characters ` è &é 'à é `"\" -PolicyMode $policyMode
    $methodContext = Compute-Method-Call @reportParams -MethodCall $call
    $localContext.merge($methodContext)
  } else {
    Rudder-Report-NA @reportParams
  }
}
