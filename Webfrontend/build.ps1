function Resolve-Css-References([String] $contents, [ref][int] $pNumRef, $parentPath, $resolveWeb){       
    Write-Host "New File ----------------"
    $regex = [regex] 'url\([''"]?([^)]+?)[''"]?\)'
    $allmatches = $regex.Matches($contents);
    $allmatches | ForEach-Object {
        $url=$_.groups[1].Value
        if (!$url.StartsWith("data:")){
            $pNumRef.Value++ 
            Write-Host $_.groups[0].Value
            Write-Host $_.groups[1].Value
            $urlContent=""
            if ($url.StartsWith("://") -or $url.StartsWith("http")){
                #content is online, get it via http-call
                if ($resolveWeb -eq 1){
                    $urlContent=(Invoke-webrequest -URI $url).Content
                }
            } else {
                if ($url.StartsWith("..") -or $url.StartsWith("./") -or $url.StartsWith("/")){
                    $fPath="$($parentPath.Substring(0, $parentPath.LastIndexOf("/")))/$url"
                    if ($fPath.Contains('#')){
                        $fPath=$fPath.Substring(0,$fPath.IndexOf('#'))
                    }
                    if ($fPath.Contains('?')){
                        $fPath=$fPath.Substring(0,$fPath.IndexOf('?'))
                    }
                    Write-Host "Reading $fPath"
                    $urlContent=Get-Content -Encoding Byte -Path $fPath
                } else {
                    if ($url.Contains('#')){
                        $url=$url.Substring(0,$url.IndexOf('#'))
                    }
                    if ($url.Contains('?')){
                        $url=$url.Substring(0,$url.IndexOf('?'))
                    }
                    Write-Host "Reading $url"
                    $urlContent=Get-Content -Encoding Byte -Path $url
                }
            }
            if ($urlContent -ne ""){
                $token="url(data:application/octet-stream;base64,$([Convert]::ToBase64String($urlContent)))"
                $contents=$contents.Replace($($_.groups[0].Value),$token)
            }                     
        }
    }

	return $contents
}

function ConvertTo-Base64
{
    param
    (
        [string] $SourceFilePath,
        [string] $TargetFilePath
    )
 
    $SourceFilePath = Resolve-PathSafe $SourceFilePath
    $TargetFilePath = Resolve-PathSafe $TargetFilePath
     
    $bufferSize = 9000 # should be a multiplier of 3
    $buffer = New-Object byte[] $bufferSize
     
    $reader = [System.IO.File]::OpenRead($SourceFilePath)
    $writer = [System.IO.File]::CreateText($TargetFilePath)
     
    $bytesRead = 0
    do
    {
        $bytesRead = $reader.Read($buffer, 0, $bufferSize);
        $writer.Write([Convert]::ToBase64String($buffer, 0, $bytesRead));
    } while ($bytesRead -eq $bufferSize);
     
    $reader.Dispose()
    $writer.Dispose()
}

function Resolve-PathSafe
{
    param
    (
        [string] $Path
    )
      
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Format-FileSize([int]$size) {    
    if ($size -gt 1TB) {[string]::Format("{0:0.00} TB", $size / 1TB)}
    elseif ($size -gt 1GB) {[string]::Format("{0:0.00} GB", $size / 1GB)}
    elseif ($size -gt 1MB) {[string]::Format("{0:0.00} MB", $size / 1MB)}
    elseif ($size -gt 1KB) {[string]::Format("{0:0.00} kB", $size / 1KB)}
    elseif ($size -gt 0) {[string]::Format("{0:0.00} B", $size)}
    else {""}
}

cls


$resolveWeb=0
$inputfile="index.html"
$content=Get-Content -Path $inputfile
$reg = '<(?<type>script|link)\s+(src|href)="(?<path>[^"]+)".+?((rel|type)="(?<subtype>[^"]+)")?(</script>|>)'
$numRef=0
gc $inputfile | Select-String -Pattern $reg -AllMatches | ForEach-Object {
    #Write-Host "$($_.matches.groups[6].value)"
    #Full match $($_.matches.groups[0].value)  
    $numRef++      
    $url=$($_.matches.groups[6].value)
    $inlineContent=""
    if ($url.StartsWith("://") -or $url.StartsWith("http")){
        #content is online, get it via http-call
        if ($resolveWeb -eq 1){
            Write-Host "Reading from web $url"
            $inlineContent=(Invoke-webrequest -URI $url).Content
        }
    } else {
        $inlineContent=Get-Content -Path $url
    }
    $token=""
    if ($($_.matches.groups[5].value) -eq "link"){
        #css
        $inlineContent=Resolve-Css-References($inlineContent)([ref] $numRef)($url)($resolveWeb)
        $token="<style type='text/css'>"+$inlineContent+"</style>"
    } else {
        #js
        if ($url -ne "../../status.json" -and $url -ne "../../securitymaterial.json" -and $url -ne "../../alertrules.json" -and $url -ne "../../scheduler.json" -and $url -ne "../../iFlowPackageContent.json" -and $url -ne "../../diffResult.json") {
            $token="<script type='text/javascript'>"+$inlineContent+"</script>"
        } else {
            $token=""
        }
    }

    if ($inlineContent -ne ""){
        $content=$content.Replace($($_.matches.groups[0].value),$token)
    }
} 
$targetfile="./dist/index.html"
$content | out-file $targetfile
Write-Host "======================================="
Write-Host "Successfully packed '$inputfile' to '$targetfile'. Size of target file: $(Format-FileSize((Get-Item $targetfile).length)). Resolved $numRef references."
ConvertTo-Base64($targetfile)("./dist/index_base64.txt")
Write-Host "Created Base64 file for CPI import."


$b64 = get-content -Path "./dist/index_base64.txt" | Out-String
$b64Arr = $b64.ToCharArray()
$out = "def staticContent = "
$blockSize=65000
$factor=$b64Arr.Length/$blockSize
for ($i=0; $i -lt $factor; $i++) {
    $start=$i*$blockSize
    $end=($i+1)*$blockSize-1
    $line=$(-join $b64Arr[$start..$end])
    $line=$line.Replace("`r`n","").Trim()
    $out+= "`"$($line)`" +`r`n"
    Write-Progress -Activity "Splitting base64 content" -PercentComplete ($i/$factor*100)
}
$start=$factor*$blockSize
$out+= "`"$(-join $b64Arr[$start..$b64Arr.Length])`""
$out | out-file "./dist/staticContent.groovy"