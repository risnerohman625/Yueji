package cn.yueji.balance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Yueji's visual language in one place: colours, type scale, spacing and the handful of surfaces every page is
 * built from. Pages only assemble these parts, so corners, padding and shadows never drift between screens.
 */
final class Ui {
    // A calm lavender canvas, near-white surfaces and one saturated purple used only where it carries meaning.
    static int BG = 0xFFF5F3F9;
    static int SURFACE = 0xFFFFFFFF;
    static int INK = 0xFF241F2E;
    static int INK_SOFT = 0xFF4A4258;
    static int MUTED = 0xFF8C8598;
    static int FAINT = 0xFFB0AABB;
    static int PURPLE = 0xFF7C5CC4;
    static int PURPLE_DEEP = 0xFF63449F;
    static int PURPLE_SOFT = 0xFFEDE6F8;
    static int PURPLE_TINT = 0xFFF6F2FC;
    static int ACCENT = PURPLE;
    static int ACCENT_DEEP = PURPLE_DEEP;
    static int ACCENT_SOFT = PURPLE_SOFT;
    static int ACCENT_TINT = PURPLE_TINT;
    static final int RIPPLE = 0x1F000000;
    static int BORDER = 0xFFEDE9F3;
    static int RED = 0xFFD9556E;
    static int RED_SOFT = 0xFFFDECEF;
    static int GREEN = 0xFF2E9B72;
    static int GREEN_SOFT = 0xFFE6F5EF;
    static int AMBER = 0xFFB07A32;
    static int AMBER_SOFT = 0xFFFBF1E3;
    static int HERO_A = 0xFF6D4DB8, HERO_B = 0xFF9B78DA;
    static boolean dark;
    static final String[] PALETTES={"紫色","蓝色","黄色","青色","玫瑰"};
    static final int[] SWATCHES={0xFF7C5CC4,0xFF397EC5,0xFFA77C16,0xFF258A82,0xFFBA5476};
    static int blend(int a,int b,float ratio){
        return Color.rgb(Math.round(Color.red(a)*(1-ratio)+Color.red(b)*ratio),
            Math.round(Color.green(a)*(1-ratio)+Color.green(b)*ratio),
            Math.round(Color.blue(a)*(1-ratio)+Color.blue(b)*ratio));
    }
    static void configure(MainActivity m){
        int mode=m.getPreferences(0).getInt("appearance",0),palette=m.getPreferences(0).getInt("palette",0);
        if(palette<0||palette>=SWATCHES.length)palette=0;
        dark=mode==2||mode==0&&(m.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int base=SWATCHES[palette];
        BG=dark?0xFF15151A:blend(Color.WHITE,base,.045f);
        SURFACE=dark?0xFF24242D:Color.WHITE;
        INK=dark?0xFFF4F1FA:0xFF241F2E;INK_SOFT=dark?0xFFD0CAD9:0xFF4A4258;
        MUTED=dark?0xFFABA5B8:0xFF797284;FAINT=dark?0xFF938C9F:0xFF9991A6;
        PURPLE=dark?blend(base,Color.WHITE,.36f):base;
        PURPLE_DEEP=dark?blend(base,Color.WHITE,.48f):blend(base,Color.BLACK,.15f);
        PURPLE_SOFT=dark?blend(SURFACE,base,.23f):blend(Color.WHITE,base,.14f);
        PURPLE_TINT=dark?blend(SURFACE,base,.1f):blend(Color.WHITE,base,.055f);
        ACCENT=PURPLE;ACCENT_DEEP=PURPLE_DEEP;ACCENT_SOFT=PURPLE_SOFT;ACCENT_TINT=PURPLE_TINT;
        BORDER=dark?0xFF3A3744:blend(Color.WHITE,base,.11f);
        RED=dark?0xFFFF8FA4:0xFFCC4963;GREEN=dark?0xFF65D6AA:0xFF248661;AMBER=dark?0xFFE4BD73:0xFF9A6B21;
        RED_SOFT=blend(SURFACE,RED,dark?.15f:.09f);GREEN_SOFT=blend(SURFACE,GREEN,dark?.15f:.09f);AMBER_SOFT=blend(SURFACE,AMBER,dark?.15f:.09f);
        HERO_A=blend(base,Color.BLACK,.12f);HERO_B=blend(base,Color.WHITE,.20f);
    }

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    final MainActivity m;
    Ui(MainActivity activity) { m = activity; }

    int dp(float v) { return Math.round(v * m.getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------------ paint
    static GradientDrawable round(int fill, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        return g;
    }
    static GradientDrawable roundStroke(int fill, int stroke, float radius, float width) {
        GradientDrawable g = round(fill, radius);
        g.setStroke(Math.max(1, Math.round(width)), stroke);
        return g;
    }
    static GradientDrawable hero(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{HERO_A, HERO_B});
        g.setCornerRadius(radius);
        return g;
    }
    RippleDrawable press(View host, GradientDrawable background) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(18 * m.getResources().getDisplayMetrics().density);
        RippleDrawable r = new RippleDrawable(ColorStateList.valueOf(0x22805FC7), background, mask);
        return r;
    }

    // ------------------------------------------------------------------ text
    TextView text(String s, float size, int color, boolean medium) {
        TextView v = new TextView(m);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        v.setTextColor(color);
        v.setTypeface(medium ? MEDIUM : Typeface.create("sans-serif", Typeface.NORMAL));
        v.setFontFeatureSettings("tnum");
        v.setIncludeFontPadding(false);
        return v;
    }
    /** Receives an extra letter-spaced eyebrow label above a value. */
    TextView label(String s, int color) {
        TextView v = text(s, 11.5f, color, false);
        v.setLetterSpacing(0);
        return v;
    }
    TextView number(String s, float size, int color) {
        TextView v = text(s, size, color, true);
        v.setTypeface(MEDIUM);
        return v;
    }
    /** A bounded single line, not setSingleLine(true), which enables horizontal scrolling and defeats auto-size. */
    TextView fit(TextView v,float max,float min) {
        v.setSingleLine(false);
        v.setMaxLines(1);
        v.setHorizontallyScrolling(false);
        v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        v.setAutoSizeTextTypeUniformWithConfiguration((int)min,(int)max,1,TypedValue.COMPLEX_UNIT_SP);
        return v;
    }
    void metricCell(LinearLayout row,String label,String value,int color,float max,int height) {
        LinearLayout cell=col();cell.setClipChildren(true);cell.setPadding(dp(3),dp(2),dp(3),dp(2));
        TextView title=fit(text(label,11,MUTED,false),11,9);
        cell.addView(title,new LinearLayout.LayoutParams(-1,dp(19)));
        gap(cell,4);
        TextView amount=fit(number(value,max,color),max,8);
        amount.setTag("bounded-number");
        cell.addView(amount,new LinearLayout.LayoutParams(-1,dp(height)));
        row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }

    // ------------------------------------------------------------------ layout
    LinearLayout col() { LinearLayout l = new LinearLayout(m); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() {
        LinearLayout l = new LinearLayout(m);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    void gap(LinearLayout parent, int height) { parent.addView(new View(m), new LinearLayout.LayoutParams(1, dp(height))); }
    void weight(LinearLayout row, View v) { weight(row,v,1); }
    void weight(LinearLayout row, View v, float w) {
        // Plain View uses the available AT_MOST height by default; a spacer must not stretch a scrolling row.
        row.addView(v,new LinearLayout.LayoutParams(0,v.getClass()==View.class?1:ViewGroup.LayoutParams.WRAP_CONTENT,w));
    }
    View divider() {
        View v = new View(m);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f)));
        v.setLayoutParams(p);
        return v;
    }
    void dividerIn(LinearLayout parent) {
        View v = divider();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f)));
        p.topMargin = dp(10); p.bottomMargin = dp(10);
        parent.addView(v, p);
    }

    /** White surface with a soft shadow; radius 18, padding 16 × 14. */
    LinearLayout card(LinearLayout parent) { return card(parent, true); }
    LinearLayout card(LinearLayout parent, boolean shadow) {
        LinearLayout c = col();
        c.setBackground(round(SURFACE, dp(18)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setClipToOutline(true);
        if (shadow) c.setElevation(dp(1.5f));
        if (parent != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = dp(12);
            parent.addView(c, p);
        }
        return c;
    }
    LinearLayout tintedCard(LinearLayout parent, int fill, int radius) {
        LinearLayout c = col();
        c.setBackground(round(fill, dp(radius)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        parent.addView(c, p);
        return c;
    }

    /** Section title on the canvas, with an optional small action on the right. */
    LinearLayout section(LinearLayout parent, String title, View action) {
        LinearLayout r = row();
        r.setPadding(dp(4), dp(6), dp(4), dp(10));
        TextView t = text(title, 15, INK, true);
        t.setTypeface(MEDIUM);
        weight(r, t);
        if (action != null) r.addView(action);
        parent.addView(r);
        return r;
    }

    // ------------------------------------------------------------------ buttons
    /** Filled purple button for the page's main action. */
    TextView primary(String label, Runnable click) {
        TextView v = text(label, 14.5f, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(46));
        v.setPadding(dp(18), 0, dp(18), 0);
        v.setBackground(press(v, round(dark?HERO_A:PURPLE, dp(14))));
        v.setClickable(true);
        v.setOnClickListener(x -> click.run());
        return v;
    }
    /** Quiet button: purple text on a pale field. */
    TextView secondary(String label, Runnable click) {
        TextView v = text(label, 14, PURPLE_DEEP, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(42));
        v.setPadding(dp(14), 0, dp(14), 0);
        v.setBackground(press(v, round(PURPLE_SOFT, dp(13))));
        v.setClickable(true);
        v.setOnClickListener(x -> click.run());
        return v;
    }
    /** Compact text action used inside cards and section headers. */
    TextView link(String label, Runnable click) {
        TextView v = text(label, 13, PURPLE, true);
        v.setPadding(dp(8), dp(6), dp(2), dp(6));
        v.setClickable(true);
        v.setOnClickListener(x -> click.run());
        return v;
    }
    /** A row that behaves like a link: label, trailing value and a chevron. */
    TextView chevron(int color) { return text("›", 18, color, false); }

    LinearLayout listRow(LinearLayout parent, String title, String value, Runnable click) {
        LinearLayout r = row();
        r.setPadding(dp(2), dp(13), dp(2), dp(13));
        r.addView(text(title, 14, INK, false));
        weight(r, new View(m));
        if (value != null && !value.isEmpty()) r.addView(text(value, 13, MUTED, false));
        TextView arrow = text("›", 17, FAINT, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = dp(6);
        r.addView(arrow, p);
        if (click != null) { r.setClickable(true); r.setOnClickListener(x -> click.run()); }
        parent.addView(r);
        return r;
    }

    ImageView logo(String type, int size) {
        ImageView v = new ImageView(m);
        int icon;
        switch (type) {
            case "工商银行": icon = R.drawable.logo_icbc; break;
            case "农业银行": icon = R.drawable.logo_abc; break;
            case "中国银行": icon = R.drawable.logo_boc; break;
            case "建设银行": icon = R.drawable.logo_ccb; break;
            case "邮储银行": icon = R.drawable.logo_psbc; break;
            case "交通银行": icon = R.drawable.logo_comm; break;
            case "招商银行": icon = R.drawable.logo_cmb; break;
            case "陕西农信": icon = R.drawable.logo_sxrccu; break;
            case "支付宝": icon = R.drawable.logo_alipay; break;
            case "微信": icon = R.drawable.logo_wechat; break;
            default: icon = R.drawable.logo_other; break;
        }
        v.setImageResource(icon);
        v.setContentDescription(type + "图标");
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setPadding(dp(5), dp(5), dp(5), dp(5));
        v.setBackground(round(PURPLE_TINT, dp(12)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(size), dp(size));
        v.setLayoutParams(p);
        return v;
    }

    /** Small rounded tag such as “已隐藏” or “待数据”. */
    TextView tag(String s, int color, int fill) {
        TextView v = text(s, 10.5f, color, true);
        v.setPadding(dp(7), dp(3), dp(7), dp(3));
        v.setBackground(round(fill, dp(7)));
        return v;
    }
}
