param(
    [string]$RepoRoot = ".",
    [string]$OutputRoot = "src-decompiled",
    [string]$FernflowerJar,
    [switch]$InPlace
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BasePath,
        [Parameter(Mandatory = $true)]
        [string]$ChildPath
    )

    $resolvedBase = [System.IO.Path]::GetFullPath($BasePath)
    return [System.IO.Path]::GetFullPath((Join-Path $resolvedBase $ChildPath))
}

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BasePath,
        [Parameter(Mandatory = $true)]
        [string]$TargetPath
    )

    $resolvedBase = [System.IO.Path]::GetFullPath($BasePath).TrimEnd("\")
    $resolvedTarget = [System.IO.Path]::GetFullPath($TargetPath)

    if ($resolvedTarget.StartsWith($resolvedBase + "\", [System.StringComparison]::OrdinalIgnoreCase)) {
        return $resolvedTarget.Substring($resolvedBase.Length + 1)
    }

    if ($resolvedTarget.Equals($resolvedBase, [System.StringComparison]::OrdinalIgnoreCase)) {
        return "."
    }

    throw "Path '$resolvedTarget' is not inside '$resolvedBase'."
}

function Test-IsWithinPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ParentPath,
        [Parameter(Mandatory = $true)]
        [string]$ChildPath
    )

    $resolvedParent = [System.IO.Path]::GetFullPath($ParentPath).TrimEnd("\")
    $resolvedChild = [System.IO.Path]::GetFullPath($ChildPath)

    return $resolvedChild.Equals($resolvedParent, [System.StringComparison]::OrdinalIgnoreCase) -or
        $resolvedChild.StartsWith($resolvedParent + "\", [System.StringComparison]::OrdinalIgnoreCase)
}

function Find-FernflowerJar {
    $candidates = @(
        "C:\Program Files\JetBrains\IntelliJ IDEA*\plugins\java-decompiler\lib\java-decompiler.jar",
        "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition*\plugins\java-decompiler\lib\java-decompiler.jar",
        (Join-Path $env:LOCALAPPDATA "Programs\JetBrains\IntelliJ IDEA*\plugins\java-decompiler\lib\java-decompiler.jar"),
        (Join-Path $env:LOCALAPPDATA "Programs\JetBrains\IntelliJ IDEA Community Edition*\plugins\java-decompiler\lib\java-decompiler.jar")
    )

    $matches = foreach ($pattern in $candidates) {
        Get-Item -Path $pattern -ErrorAction SilentlyContinue
    }

    $selected = $matches | Sort-Object LastWriteTime -Descending | Select-Object -First 1

    if ($null -eq $selected) {
        throw "Unable to locate IntelliJ's Fernflower jar. Pass -FernflowerJar to the script."
    }

    return $selected.FullName
}

$resolvedRepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$resolvedOutputRoot = if ($InPlace) { $resolvedRepoRoot } else { Resolve-FullPath -BasePath $resolvedRepoRoot -ChildPath $OutputRoot }
$resolvedFernflowerJar = if ($FernflowerJar) { [System.IO.Path]::GetFullPath($FernflowerJar) } else { Find-FernflowerJar }

if (-not (Test-Path -LiteralPath $resolvedFernflowerJar -PathType Leaf)) {
    throw "Fernflower jar not found at '$resolvedFernflowerJar'."
}

if (-not (Test-IsWithinPath -ParentPath $resolvedRepoRoot -ChildPath $resolvedOutputRoot)) {
    throw "Output path '$resolvedOutputRoot' must stay inside '$resolvedRepoRoot'."
}

if (-not $InPlace) {
    if ($resolvedOutputRoot.Equals($resolvedRepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clear the repository root. Use -InPlace to write beside the .class files."
    }

    if (Test-Path -LiteralPath $resolvedOutputRoot) {
        Remove-Item -LiteralPath $resolvedOutputRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Path $resolvedOutputRoot | Out-Null
}

$classDirectories = Get-ChildItem -Path $resolvedRepoRoot -Recurse -File -Filter *.class |
    Where-Object {
        $fullName = $_.FullName
        -not $fullName.Contains("\.git\") -and
        -not $fullName.Contains("\.vscode\") -and
        -not $fullName.Contains("\src-decompiled\")
    } |
    Select-Object -ExpandProperty DirectoryName -Unique |
    Sort-Object

if (-not $classDirectories) {
    throw "No .class files found under '$resolvedRepoRoot'."
}

$decompileRoots = New-Object System.Collections.Generic.List[string]

foreach ($candidateDirectory in ($classDirectories | Sort-Object { $_.Length })) {
    $hasCoveredAncestor = $false

    foreach ($existingRoot in $decompileRoots) {
        if (
            -not $candidateDirectory.Equals($existingRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Test-IsWithinPath -ParentPath $existingRoot -ChildPath $candidateDirectory)
        ) {
            $hasCoveredAncestor = $true
            break
        }
    }

    if (-not $hasCoveredAncestor) {
        $decompileRoots.Add($candidateDirectory)
    }
}

Write-Host "Fernflower jar: $resolvedFernflowerJar"
Write-Host "Repository root: $resolvedRepoRoot"
Write-Host "Output root: $resolvedOutputRoot"
Write-Host "Directories to process: $($decompileRoots.Count)"

$mainClass = "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
$processed = 0

foreach ($sourceDirectory in $decompileRoots) {
    $processed += 1
    $relativeDirectory = Get-RelativePath -BasePath $resolvedRepoRoot -TargetPath $sourceDirectory
    $destinationDirectory = if ($InPlace) {
        $sourceDirectory
    } else {
        Resolve-FullPath -BasePath $resolvedOutputRoot -ChildPath $relativeDirectory
    }

    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null

    Write-Host ("[{0}/{1}] {2}" -f $processed, $decompileRoots.Count, $relativeDirectory)
    & java "-cp" $resolvedFernflowerJar $mainClass $sourceDirectory $destinationDirectory

    if ($LASTEXITCODE -ne 0) {
        throw "Fernflower failed while processing '$relativeDirectory'."
    }
}

$javaCount = (Get-ChildItem -Path $resolvedOutputRoot -Recurse -File -Filter *.java | Measure-Object).Count
Write-Host "Generated $javaCount Java source files."
