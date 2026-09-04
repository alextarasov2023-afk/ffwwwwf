Add-Type -AssemblyName System.Drawing

$dir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\src\main\resources\assets\wonderful\textures\gui\category"))
$S = 2048   # холст рисования (суперсэмпл)
$OUT = 512  # итоговый размер

function New-Pen([float]$w) {
    $p = New-Object System.Drawing.Pen([System.Drawing.Color]::White, $w)
    $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $p.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
    $p.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $p
}

function DrawPoly($g, $p, $pts) {
    for ($i = 0; $i -lt $pts.Count - 1; $i++) {
        $g.DrawLine($p, $pts[$i][0], $pts[$i][1], $pts[$i+1][0], $pts[$i+1][1])
    }
}

function New-Icon([string]$name, [scriptblock]$draw) {
    $big = New-Object System.Drawing.Bitmap($S, $S)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    & $draw $g
    $g.Dispose()

    $small = New-Object System.Drawing.Bitmap($OUT, $OUT)
    $g2 = [System.Drawing.Graphics]::FromImage($small)
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g2.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g2.DrawImage($big, 0, 0, $OUT, $OUT)
    $g2.Dispose(); $big.Dispose()

    $small.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $small.Dispose()
    Write-Output "icon $name done"
}

$white = [System.Drawing.Brushes]::White

# COMBAT — скрещённые мечи
New-Icon 'combat' { param($g)
    $blade = New-Pen 150
    $guard = New-Pen 105
    # меч 1 (наклон вправо-вверх)
    $g.DrawLine($blade, 1600, 400, 480, 1520)
    $g.DrawLine($guard, 548, 1184, 816, 1452)
    $g.FillEllipse($white, 409-110, 1591-110, 220, 220)
    # меч 2 (зеркально)
    $g.DrawLine($blade, 448, 400, 1568, 1520)
    $g.DrawLine($guard, 1500, 1184, 1232, 1452)
    $g.FillEllipse($white, 1639-110, 1591-110, 220, 220)
}

# MOVEMENT — бегущий человек
New-Icon 'movement' { param($g)
    $g.FillEllipse($white, 1330-190, 480-190, 380, 380)   # голова
    $p = New-Pen 165
    DrawPoly $g $p @(@([int]1170,[int]790), @([int]900,[int]1160))                                # корпус
    DrawPoly $g $p @(@([int]1160,[int]830), @([int]1430,[int]900), @([int]1590,[int]760))         # передняя рука
    DrawPoly $g $p @(@([int]1130,[int]900), @([int]880,[int]1000), @([int]700,[int]860))          # задняя рука
    DrawPoly $g $p @(@([int]900,[int]1160), @([int]1230,[int]1330), @([int]1330,[int]1690))       # передняя нога
    DrawPoly $g $p @(@([int]900,[int]1160), @([int]660,[int]1420), @([int]400,[int]1600))         # задняя нога
}

# PLAYER — человек (голова + плечи)
New-Icon 'player' { param($g)
    $g.FillEllipse($white, 1024-310, 420, 620, 620)       # голова, центр (1024,730)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(330, 1150, 1388, 1388, 180, 180)         # купол плеч
    $path.CloseFigure()
    $g.FillPath($white, $path)
    $path.Dispose()
}

# RENDER — глаз
New-Icon 'render' { param($g)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
    $path.AddBezier(224, 1024, 680, 520, 1368, 520, 1824, 1024)     # верх века
    $path.AddBezier(1824, 1024, 1368, 1528, 680, 1528, 224, 1024)   # низ века
    $path.AddEllipse(1024-330, 1024-330, 660, 660)                  # дырка под радужку
    $g.FillPath($white, $path)
    $g.FillEllipse($white, 1024-165, 1024-165, 330, 330)            # зрачок
    $path.Dispose()
}

# MISC — ползунки настроек
New-Icon 'misc' { param($g)
    $p = New-Pen 125
    foreach ($y in 620, 1024, 1428) { $g.DrawLine($p, 400, $y, 1648, $y) }
    $knobs = @(@([int]780,[int]620), @([int]1330,[int]1024), @([int]880,[int]1428))
    # вырезаем зазор вокруг ручек
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $erase = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0,0,0,0))
    foreach ($k in $knobs) { $g.FillEllipse($erase, $k[0]-215, $k[1]-215, 430, 430) }
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    foreach ($k in $knobs) { $g.FillEllipse($white, $k[0]-150, $k[1]-150, 300, 300) }
    $erase.Dispose()
}

Write-Output "ALL DONE -> $dir"
