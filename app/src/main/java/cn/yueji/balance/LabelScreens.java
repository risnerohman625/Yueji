package cn.yueji.balance;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Separate label grid and editor pages, plus the compact label picker used
 * by the income form. Everything is built from Ui parts; colours are read when the views are built.
 */
final class LabelScreens {
    private final MainActivity m;
    private final Ui ui;

    // editor state; lives only as long as this instance (a recreated activity shows the plain grid again)
    private boolean editing;
    private String editOld;
    private String draftName = "";
    private String draftIcon;

    LabelScreens(MainActivity activity) {
        m = activity;
        ui = activity.ui;
    }

    private int dp(float v) { return m.dp(v); }

    // ------------------------------------------------------------------ shared drawing
    private RippleDrawable ripple(GradientDrawable bg, float radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(Ui.RIPPLE), bg, mask);
    }

    private static GradientDrawable fill(int color, float radius, int stroke, float strokePx) {
        GradientDrawable g = Ui.round(color, radius);
        if (strokePx > 0) g.setStroke(Math.max(1, Math.round(strokePx)), stroke);
        return g;
    }

    private TextView emoji(String glyph, float sp) {
        TextView t = new TextView(m);
        t.setText(glyph);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    // ------------------------------------------------------------------ the page
    void page() {
        Ledger.Data d = m.data;
        m.top("标签管理", "＋ 新增", ()->open(null));
        View add=m.body.getChildAt(0);if(add instanceof ViewGroup)((ViewGroup)add).getChildAt(1).setContentDescription("新增标签");
        TextView hint = ui.text("长按标签修改名称和图标，点击查看该类收入。每个图标只供一个标签使用。", 12, Ui.MUTED, false);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        m.body.addView(hint);

        LinearLayout card = ui.card(m.body);
        LinearLayout head = ui.row();
        head.setPadding(0, 0, 0, dp(12));
        ui.weight(head, ui.text("收入标签", 15, Ui.INK, true));
        head.addView(ui.text(d.categories.size() + " / " + LabelRules.capacity(), 12.5f, Ui.MUTED, false));
        card.addView(head);

        FlowLayout grid = new FlowLayout(m).columns(4).gaps(dp(8), dp(8));
        for (String name : d.categories) grid.addView(labelTile(d, name), new ViewGroup.MarginLayoutParams(-1, dp(70)));
        card.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }
    void reset(){editing=false;editOld=null;}
    void editPage(){
        if(!editing||editOld!=null&&!m.data.categories.contains(editOld)){m.back();return;}
        m.top(editOld==null?"新增收入标签":"编辑收入标签",null,null);editor(m.data);
    }
    android.os.Bundle saveState(){
        android.os.Bundle b=new android.os.Bundle();b.putBoolean("editing",editing);b.putString("old",editOld);b.putString("name",draftName);b.putString("icon",draftIcon);return b;
    }
    void restoreState(android.os.Bundle b){
        if(b==null)return;editing=b.getBoolean("editing");editOld=b.getString("old");draftName=b.getString("name","");draftIcon=b.getString("icon");
    }

    private View labelTile(Ledger.Data d, String name) {
        boolean current = editing && name.equals(editOld);
        LinearLayout tile = ui.col();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setBackground(ripple(fill(Ui.ACCENT_SOFT, dp(18), Ui.ACCENT, current ? dp(2) : 0), dp(18)));
        tile.setClickable(true);
        tile.setContentDescription("标签" + name);
        tile.setOnClickListener(v -> {m.categoryFocus=name;m.go("income_category");});
        tile.setOnLongClickListener(v->{open(name);return true;});
        tile.addView(emoji(d.iconOf(name), 25), new LinearLayout.LayoutParams(-2, -2));
        ui.gap(tile, 5);
        TextView title = ui.fit(ui.text(name, 11.5f, Ui.INK, true),11,8);
        title.setGravity(Gravity.CENTER);
        tile.addView(title, new LinearLayout.LayoutParams(-1, dp(20)));
        return tile;
    }

    private View addTile(Ledger.Data d) {
        boolean full = LabelRules.full(d);
        LinearLayout tile = ui.col();
        tile.setGravity(Gravity.CENTER);
        GradientDrawable dashed = fill(Color.TRANSPARENT, dp(18), 0, 0);
        dashed.setStroke(dp(1.5f), full ? Ui.BORDER : Ui.FAINT, dp(5), dp(4));
        tile.setBackground(ripple(dashed, dp(18)));
        tile.setClickable(true);
        tile.setContentDescription("新增标签");
        tile.setOnClickListener(v -> open(null));
        TextView plus = ui.text("＋", 26, full ? Ui.FAINT : Ui.ACCENT, false);
        plus.setGravity(Gravity.CENTER);
        tile.addView(plus, new LinearLayout.LayoutParams(-2, -2));
        ui.gap(tile, 4);
        tile.addView(ui.text(full ? "已满" : "新增", 13, full ? Ui.FAINT : Ui.ACCENT_DEEP, true), new LinearLayout.LayoutParams(-2, -2));
        return tile;
    }

    private void open(String name) {
        Ledger.Data d = m.data;
        if (name == null) {
            if (LabelRules.full(d)) { m.toast(LabelRules.fullMessage()); return; }
            editOld = null;
            draftName = "";
            draftIcon = LabelRules.firstFree(d, null);
        } else {
            editOld = name;
            draftName = name;
            draftIcon = d.iconOf(name);
        }
        editing = true;
        m.go("label_edit");
    }

    private void close() {
        editing = false;
        editOld = null;
        m.back();
    }

    private void reorder(int from, int to) {
        try {
            Ledger.Data candidate = m.copy();
            LabelRules.move(candidate, from, to);
            m.commit(candidate);
        } catch (Exception e) {
            m.error(e);
        }
        m.render();
    }

    // ------------------------------------------------------------------ inline editor
    private void editor(Ledger.Data d) {
        LinearLayout card = ui.card(m.body);
        card.setContentDescription("标签编辑");
        card.addView(ui.text("名称与图标", 15, Ui.INK, true));
        ui.gap(card, 12);

        // preview tile + name field side by side
        LinearLayout line = ui.row();
        TextView preview = emoji(draftIcon == null ? "？" : draftIcon, 28);
        preview.setBackground(fill(Ui.ACCENT_SOFT, dp(16), 0, 0));
        line.addView(preview, new LinearLayout.LayoutParams(dp(56), dp(56)));
        EditText name = new EditText(m);
        name.setContentDescription("标签名称");
        name.setHint("标签名称，最多 " + LabelRules.MAX_NAME + " 字");
        name.setHintTextColor(Ui.FAINT);
        name.setSingleLine(true);
        name.setTextSize(16);
        name.setTextColor(Ui.INK);
        name.setPadding(dp(13), 0, dp(13), 0);
        name.setBackground(fill(Ui.ACCENT_TINT, dp(14), Ui.BORDER, dp(1)));
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LabelRules.MAX_NAME)});
        name.setText(draftName);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { draftName = s.toString(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(56), 1);
        np.leftMargin = dp(12);
        line.addView(name, np);
        card.addView(line);

        ui.gap(card, 16);
        LinearLayout labelRow = ui.row();
        labelRow.addView(ui.text("选择图标", 12.5f, Ui.MUTED, true));
        View spacer = new View(m);
        labelRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        int used = d.categories.size() - (editOld == null ? 0 : 1);
        labelRow.addView(ui.text("已用 " + used + " / " + LabelRules.capacity(), 11.5f, Ui.FAINT, false));
        card.addView(labelRow);
        ui.gap(card, 8);

        FlowLayout library = new FlowLayout(m).columns(6).gaps(dp(6), dp(6));
        List<FrameLayout> tiles = new ArrayList<>();
        for (String icon : Ledger.ICONS) {
            FrameLayout tile = iconTile(d, icon);
            tiles.add(tile);
            tile.setOnClickListener(v -> {
                String owner = LabelRules.owner(d, icon, editOld);
                if (owner != null) { m.toast("已被「" + owner + "」使用"); return; }
                draftIcon = icon;
                preview.setText(icon);
                for (int i = 0; i < tiles.size(); i++) styleIconTile(tiles.get(i), d, Ledger.ICONS[i]);
            });
            library.addView(tile, new ViewGroup.MarginLayoutParams(-1, dp(54)));
        }
        card.addView(library, new LinearLayout.LayoutParams(-1, -2));

        ui.gap(card, 16);
        LinearLayout buttons = ui.row();
        TextView cancel = ui.secondary("取消", this::close);
        cancel.setContentDescription("取消编辑标签");
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView save = ui.primary("保存", this::save);
        save.setContentDescription("保存标签");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(46), 1);
        sp.leftMargin = dp(10);
        buttons.addView(save, sp);
        card.addView(buttons);
        if (editOld != null) {
            TextView delete = ui.text("删除这个标签", 13.5f, Ui.RED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, dp(12), 0, dp(4));
            delete.setContentDescription("删除标签");
            delete.setOnClickListener(v -> confirmDelete(editOld));
            card.addView(delete, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private FrameLayout iconTile(Ledger.Data d, String icon) {
        FrameLayout tile = new FrameLayout(m);
        tile.setClickable(true);
        tile.setContentDescription("图标" + icon);
        TextView glyph = emoji(icon, 22);
        glyph.setTag("glyph");
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-1,dp(32),Gravity.TOP);gp.topMargin=dp(2);tile.addView(glyph,gp);
        int index=java.util.Arrays.asList(Ledger.ICONS).indexOf(icon);
        TextView meaning=ui.fit(ui.text(Ledger.ICON_NAMES[index],8.5f,Ui.INK_SOFT,false),8,6);meaning.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(15),Gravity.BOTTOM);lp.bottomMargin=dp(3);tile.addView(meaning,lp);
        TextView marker = ui.text("", 8.5f, Ui.INK_SOFT, true);
        marker.setTag("marker");
        marker.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.END | Gravity.TOP);
        mp.rightMargin = dp(3);
        mp.topMargin = dp(3);
        tile.addView(marker, mp);
        styleIconTile(tile, d, icon);
        return tile;
    }

    /** Ring on the chosen icon, dimmed icon plus the first letter of the owner on icons another label uses. */
    private void styleIconTile(FrameLayout tile, Ledger.Data d, String icon) {
        String owner = LabelRules.owner(d, icon, editOld);
        boolean chosen = icon.equals(draftIcon);
        float r = dp(12);
        GradientDrawable bg;
        if (chosen) bg = fill(Ui.ACCENT_SOFT, r, Ui.ACCENT, dp(2));
        else if (owner != null) bg = fill(Ui.SURFACE, r, Ui.BORDER, dp(1));
        else bg = fill(Ui.ACCENT_TINT, r, 0, 0);
        tile.setBackground(ripple(bg, r));
        View glyph = tile.findViewWithTag("glyph");
        glyph.setAlpha(owner != null ? 0.3f : 1f);
        TextView marker = (TextView) tile.findViewWithTag("marker");
        if (owner != null) {
            marker.setVisibility(View.VISIBLE);
            marker.setText(new String(Character.toChars(owner.codePointAt(0))));
            marker.setTextColor(Ui.INK_SOFT);
            marker.setBackground(Ui.round(Ui.BORDER, dp(7)));
        } else marker.setVisibility(View.GONE);
    }

    private void save() {
        try {
            Ledger.Data candidate = m.copy();
            if (editOld == null) LabelRules.add(candidate, draftName, draftIcon);
            else LabelRules.edit(candidate, editOld, draftName, draftIcon);
            if(m.formDraft!=null&&editOld!=null&&editOld.equals(m.formDraft.values.getString("choice:收入类别")))
                m.formDraft.values.putString("choice:收入类别",draftName.trim());
            if(editOld!=null&&editOld.equals(m.categoryFocus))m.categoryFocus=draftName.trim();
            m.commit(candidate);
            editing = false;
            editOld = null;
            m.back();
        } catch (Exception e) {
            m.error(e);
        }
    }

    private void confirmDelete(String name) {
        int used = LabelRules.usage(m.data, name);
        String message = used == 0 ? "这个标签下没有收入记录。" : "该标签下的 " + used + " 笔历史收入会保留，并归入“其他”。";
        AppIcons.dialog(m).setTitle("删除标签「" + name + "」？").setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (dialog, which) -> {
                try {
                    Ledger.Data candidate = m.copy();
                    LabelRules.delete(candidate, name);
                    if(name.equals(m.categoryFocus))m.categoryFocus=LabelRules.FALLBACK;
                    m.commit(candidate);
                    editing = false;
                    editOld = null;
                    m.back();
                } catch (Exception e) {
                    m.error(e);
                }
            }).show();
    }

    // ------------------------------------------------------------------ picker for the income form
    /**
     * Wrap grid of label tiles (4 per row) with a trailing “管理” tile. The caller owns the chosen value: this only draws
     * the ring and reports taps. A null or unknown {@code selected} shows the first label as chosen without calling back.
     */
    static void picker(MainActivity m, LinearLayout parent, String selected, Consumer<String> onPick, Runnable onManage) {
        Ui ui = m.ui;
        Ledger.Data d = m.data;
        List<String> names = new ArrayList<>(d.categories);
        String[] current = {selected != null && names.contains(selected) ? selected : names.isEmpty() ? null : names.get(0)};
        FlowLayout grid = new FlowLayout(m).columns(4).gaps(m.dp(8), m.dp(8));
        List<LinearLayout> tiles = new ArrayList<>();
        for (String name : names) {
            LinearLayout tile = ui.col();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(m.dp(2), m.dp(6), m.dp(2), m.dp(6));
            tile.setClickable(true);
            tile.setContentDescription("收入类别" + name);
            TextView glyph = new TextView(m);
            glyph.setText(d.iconOf(name));
            glyph.setTextSize(22);
            glyph.setGravity(Gravity.CENTER);
            glyph.setIncludeFontPadding(false);
            tile.addView(glyph, new LinearLayout.LayoutParams(-2, -2));
            ui.gap(tile, 4);
            TextView label = ui.text(name, 11.5f, Ui.INK, true);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
            tile.setOnClickListener(v -> {
                current[0] = name;
                restyle(m, tiles, names, current[0]);
                onPick.accept(name);
            });
            tiles.add(tile);
            grid.addView(tile, new ViewGroup.MarginLayoutParams(-1, m.dp(62)));
        }
        restyle(m, tiles, names, current[0]);
        LinearLayout manage = ui.col();
        manage.setGravity(Gravity.CENTER);
        manage.setClickable(true);
        manage.setContentDescription("管理标签");
        GradientDrawable dashed = Ui.round(Color.TRANSPARENT, m.dp(14));
        dashed.setStroke(m.dp(1.2f), Ui.FAINT, m.dp(4), m.dp(3));
        manage.setBackground(dashed);
        TextView gear = ui.text("⚙", 20, Ui.ACCENT, false);
        gear.setGravity(Gravity.CENTER);
        manage.addView(gear, new LinearLayout.LayoutParams(-2, -2));
        ui.gap(manage, 4);
        manage.addView(ui.text("管理", 11.5f, Ui.ACCENT_DEEP, true), new LinearLayout.LayoutParams(-2, -2));
        manage.setOnClickListener(v -> onManage.run());
        grid.addView(manage, new ViewGroup.MarginLayoutParams(-1, m.dp(62)));
        parent.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void restyle(MainActivity m, List<LinearLayout> tiles, List<String> names, String selected) {
        float r = m.dp(14);
        for (int i = 0; i < tiles.size(); i++) {
            boolean on = names.get(i).equals(selected);
            GradientDrawable bg = on ? fill(Ui.ACCENT_SOFT, r, Ui.ACCENT, m.dp(1.5f)) : fill(Ui.ACCENT_TINT, r, Color.TRANSPARENT, m.dp(1.5f));
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(r);
            tiles.get(i).setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.RIPPLE), bg, mask));
            ((TextView) tiles.get(i).getChildAt(2)).setTextColor(on ? Ui.ACCENT_DEEP : Ui.INK);
        }
    }
}
