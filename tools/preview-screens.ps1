$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
$items=@(
    @('基金详情识别','v461-multiple-detail-images.png'),
    @('银行实际收益','v461-bank-daily-calendar.png'),
    @('收益导入核对','v461-bank-income-review.png'),
    @('资金变动记录','v461-bank-flow-records.png')
)
$width=300
$height=667
$margin=16
$top=48
$canvas=[Drawing.Bitmap]::new(($width+$margin)*$items.Count+$margin,$height+$top+$margin)
$graphics=[Drawing.Graphics]::FromImage($canvas)
$font=[Drawing.Font]::new('Microsoft YaHei UI',15,[Drawing.FontStyle]::Regular)
$brush=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(36,31,46))
try{
    $graphics.Clear([Drawing.Color]::FromArgb(245,243,249))
    $graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    for($i=0;$i -lt $items.Count;$i++){
        $x=$margin+$i*($width+$margin)
        $graphics.DrawString($items[$i][0],$font,$brush,[single]$x,[single]12)
        $path=Join-Path $project ('docs/screenshots-v461/'+$items[$i][1])
        $image=[Drawing.Image]::FromFile($path)
        try{$graphics.DrawImage($image,[Drawing.Rectangle]::new($x,$top,$width,$height))}
        finally{$image.Dispose()}
    }
    $output=Join-Path $project 'docs/screenshots-v461/preview.png'
    $canvas.Save($output,[Drawing.Imaging.ImageFormat]::Png)
    Write-Output $output
}finally{$brush.Dispose();$font.Dispose();$graphics.Dispose();$canvas.Dispose()}
