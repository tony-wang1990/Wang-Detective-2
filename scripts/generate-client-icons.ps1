param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$teal = [System.Drawing.ColorTranslator]::FromHtml('#0F766E')
$light = [System.Drawing.ColorTranslator]::FromHtml('#F8FAFC')
$white = [System.Drawing.Color]::White

function New-RoundedPath([System.Drawing.RectangleF]$rect, [float]$radius) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $radius * 2
    $path.AddArc($rect.X, $rect.Y, $diameter, $diameter, 180, 90)
    $path.AddArc($rect.Right - $diameter, $rect.Y, $diameter, $diameter, 270, 90)
    $path.AddArc($rect.Right - $diameter, $rect.Bottom - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-BrandBitmap([int]$width, [int]$height, [bool]$round, [bool]$foregroundOnly = $false) {
    $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.Color]::Transparent)

    if (-not $foregroundOnly) {
        $margin = [Math]::Max(1, [Math]::Round([Math]::Min($width, $height) * 0.04))
        $rect = [System.Drawing.RectangleF]::new($margin, $margin, $width - 2 * $margin, $height - 2 * $margin)
        $brush = [System.Drawing.SolidBrush]::new($teal)
        if ($round) {
            $graphics.FillEllipse($brush, $rect)
        } else {
            $path = New-RoundedPath $rect ([Math]::Min($width, $height) * 0.18)
            $graphics.FillPath($brush, $path)
            $path.Dispose()
        }
        $brush.Dispose()
    }

    $fontSize = [Math]::Min($width, $height) * $(if ($foregroundOnly) { 0.42 } else { 0.48 })
    $font = [System.Drawing.Font]::new('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = [System.Drawing.SolidBrush]::new($white)
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textRect = [System.Drawing.RectangleF]::new(0, -$height * 0.035, $width, $height)
    $graphics.DrawString('W', $font, $textBrush, $textRect, $format)

    $format.Dispose()
    $textBrush.Dispose()
    $font.Dispose()
    $graphics.Dispose()
    return $bitmap
}

$desktopBuild = Join-Path $root 'apps\desktop\build'
New-Item -ItemType Directory -Force -Path $desktopBuild | Out-Null
$desktopIcon = New-BrandBitmap 512 512 $false
$desktopIcon.Save((Join-Path $desktopBuild 'icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$icoBitmap = New-BrandBitmap 256 256 $false
$iconHandle = $icoBitmap.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($iconHandle)
$stream = [System.IO.File]::Create((Join-Path $desktopBuild 'icon.ico'))
$icon.Save($stream)
$stream.Dispose()
$icon.Dispose()
$icoBitmap.Dispose()
$desktopIcon.Dispose()

$androidRes = Join-Path $root 'apps\android\android\app\src\main\res'
$densities = [ordered]@{
    'mdpi' = 48
    'hdpi' = 72
    'xhdpi' = 96
    'xxhdpi' = 144
    'xxxhdpi' = 192
}
foreach ($entry in $densities.GetEnumerator()) {
    $dir = Join-Path $androidRes ("mipmap-{0}" -f $entry.Key)
    $square = New-BrandBitmap $entry.Value $entry.Value $false
    $round = New-BrandBitmap $entry.Value $entry.Value $true
    $foreground = New-BrandBitmap $entry.Value $entry.Value $false $true
    $square.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $foreground.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $square.Dispose()
    $round.Dispose()
    $foreground.Dispose()
}

Get-ChildItem $androidRes -Recurse -Filter 'splash.png' | ForEach-Object {
    $source = [System.Drawing.Image]::FromFile($_.FullName)
    $width = $source.Width
    $height = $source.Height
    $source.Dispose()
    $splash = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($splash)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear($light)
    $size = [int]([Math]::Min($width, $height) * 0.24)
    $mark = New-BrandBitmap $size $size $false
    $graphics.DrawImage($mark, [int](($width - $size) / 2), [int](($height - $size) / 2), $size, $size)
    $mark.Dispose()
    $graphics.Dispose()
    $splash.Save($_.FullName, [System.Drawing.Imaging.ImageFormat]::Png)
    $splash.Dispose()
}

Write-Output 'Generated Windows and Android client icons.'
