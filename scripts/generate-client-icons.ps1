param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$amber = [System.Drawing.ColorTranslator]::FromHtml('#F4C44E')
$navy = [System.Drawing.ColorTranslator]::FromHtml('#102A43')
$blue = [System.Drawing.ColorTranslator]::FromHtml('#155EEF')
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
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.Color]::Transparent)

    if (-not $foregroundOnly) {
        $margin = [Math]::Max(1, [Math]::Round([Math]::Min($width, $height) * 0.04))
        $rect = [System.Drawing.RectangleF]::new($margin, $margin, $width - 2 * $margin, $height - 2 * $margin)
        $brush = [System.Drawing.SolidBrush]::new($amber)
        if ($round) {
            $graphics.FillEllipse($brush, $rect)
        } else {
            $path = New-RoundedPath $rect ([Math]::Min($width, $height) * 0.18)
            $graphics.FillPath($brush, $path)
            $path.Dispose()
        }
        $brush.Dispose()
    }

    $size = [Math]::Min($width, $height)
    $centerX = $width * 0.46
    $centerY = $height * 0.43
    $radius = $size * 0.275

    $handlePen = [System.Drawing.Pen]::new($navy, [Math]::Max(2, $size * 0.105))
    $handlePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $handlePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawLine(
        $handlePen,
        [float]($centerX + $radius * 0.68),
        [float]($centerY + $radius * 0.68),
        [float]($width * 0.78),
        [float]($height * 0.78)
    )
    $handlePen.Dispose()

    $lensOuter = [System.Drawing.SolidBrush]::new($navy)
    $graphics.FillEllipse(
        $lensOuter,
        [float]($centerX - $radius),
        [float]($centerY - $radius),
        [float]($radius * 2),
        [float]($radius * 2)
    )
    $lensOuter.Dispose()

    $ring = $size * 0.052
    $lensInner = [System.Drawing.SolidBrush]::new($white)
    $graphics.FillEllipse(
        $lensInner,
        [float]($centerX - $radius + $ring),
        [float]($centerY - $radius + $ring),
        [float](($radius - $ring) * 2),
        [float](($radius - $ring) * 2)
    )
    $lensInner.Dispose()

    $fontSize = $size * 0.285
    $font = [System.Drawing.Font]::new('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = [System.Drawing.SolidBrush]::new($blue)
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textRect = [System.Drawing.RectangleF]::new(
        [float]($centerX - $radius),
        [float]($centerY - $radius - $size * 0.018),
        [float]($radius * 2),
        [float]($radius * 2)
    )
    $graphics.DrawString('W', $font, $textBrush, $textRect, $format)

    $format.Dispose()
    $textBrush.Dispose()
    $font.Dispose()
    $graphics.Dispose()
    return $bitmap
}

function Save-MultiSizeIcon([string]$path, [int[]]$sizes) {
    $images = @()
    foreach ($size in $sizes) {
        $bitmap = New-BrandBitmap $size $size $false
        $stream = [System.IO.MemoryStream]::new()
        $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
        $images += ,$stream.ToArray()
        $stream.Dispose()
        $bitmap.Dispose()
    }

    $file = [System.IO.File]::Create($path)
    $writer = [System.IO.BinaryWriter]::new($file)
    $writer.Write([uint16]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]$sizes.Count)

    $offset = 6 + (16 * $sizes.Count)
    for ($index = 0; $index -lt $sizes.Count; $index++) {
        $size = $sizes[$index]
        $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
        $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]32)
        $writer.Write([uint32]$images[$index].Length)
        $writer.Write([uint32]$offset)
        $offset += $images[$index].Length
    }

    foreach ($image in $images) {
        $writer.Write($image)
    }
    $writer.Dispose()
    $file.Dispose()
}

$desktopBuild = Join-Path $root 'apps\desktop\build'
New-Item -ItemType Directory -Force -Path $desktopBuild | Out-Null
$desktopIcon = New-BrandBitmap 512 512 $false
$desktopIcon.Save((Join-Path $desktopBuild 'icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Save-MultiSizeIcon (Join-Path $desktopBuild 'icon.ico') @(16, 24, 32, 48, 64, 128, 256)
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
