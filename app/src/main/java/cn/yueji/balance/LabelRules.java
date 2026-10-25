package cn.yueji.balance;

import java.util.List;

/** Pure rules for income labels (categories) and their one-icon-each library; every method works on a Ledger.Data copy. */
final class LabelRules {
    static final String FALLBACK = "其他";
    static final int MAX_NAME = 30;

    static String clean(String raw) { return raw == null ? "" : raw.trim(); }

    static int capacity() { return Ledger.ICONS.length; }

    static String fullMessage() { return "标签已满（最多 " + capacity() + " 个），请先删除不用的标签"; }

    static boolean full(Ledger.Data d) { return d.categories.size() >= capacity(); }

    /** Number of income records filed under a label. */
    static int usage(Ledger.Data d, String name) {
        int n = 0;
        for (Ledger.Entry e : d.entries) if (e.income && e.category.equals(name)) n++;
        return n;
    }

    /** Name of the label that already owns {@code icon}, or null when it is free (or only owned by {@code except}). */
    static String owner(Ledger.Data d, String icon, String except) {
        for (String c : d.categories) {
            if (c.equals(except)) continue;
            if (icon.equals(d.categoryIcons.get(c))) return c;
        }
        return null;
    }

    /** First icon of the library nobody uses (apart from {@code except}), or null if the library is exhausted. */
    static String firstFree(Ledger.Data d, String except) {
        for (String i : Ledger.ICONS) if (owner(d, i, except) == null) return i;
        return null;
    }

    static void checkName(Ledger.Data d, String name, String old) {
        if (name.isEmpty()) throw new IllegalArgumentException("请填写标签名称");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("标签名称不能超过 " + MAX_NAME + " 个字");
        if (!name.equals(old) && d.categories.contains(name)) throw new IllegalArgumentException("标签「" + name + "」已存在");
    }

    static void checkIcon(Ledger.Data d, String icon, String except) {
        if (icon == null || !Ledger.ICON_SET.contains(icon)) throw new IllegalArgumentException("请选择一个图标");
        String owner = owner(d, icon, except);
        if (owner != null) throw new IllegalArgumentException("这个图标已被「" + owner + "」使用，请换一个");
    }

    static void add(Ledger.Data d, String rawName, String icon) {
        String name = clean(rawName);
        if (full(d)) throw new IllegalArgumentException(fullMessage());
        checkName(d, name, null);
        checkIcon(d, icon, null);
        d.categories.add(name);
        d.categoryIcons.put(name, icon);
    }

    /** Renames and/or re-icons a label in place; income records follow the new name and the label keeps its position. */
    static void edit(Ledger.Data d, String old, String rawName, String icon) {
        String name = clean(rawName);
        int index = d.categories.indexOf(old);
        if (index < 0) throw new IllegalArgumentException("标签不存在");
        checkName(d, name, old);
        checkIcon(d, icon, old);
        d.categories.set(index, name);
        d.categoryIcons.remove(old);
        d.categoryIcons.put(name, icon);
        if (!name.equals(old))
            for (Ledger.Entry e : d.entries)
                if (e.income && e.category.equals(old)) { e.category = name; if (e.title.equals(old)) e.title = name; }
    }

    /** Removes a label; its income records move to “其他” (created when missing). The list never ends up empty. */
    static void delete(Ledger.Data d, String old) {
        int index = d.categories.indexOf(old);
        if (index < 0) throw new IllegalArgumentException("标签不存在");
        int used = usage(d, old);
        if (FALLBACK.equals(old) && used > 0) throw new IllegalArgumentException("「其他」下还有 " + used + " 笔收入，不能删除");
        if (FALLBACK.equals(old) && d.categories.size() == 1) throw new IllegalArgumentException("至少保留一个标签");
        d.categories.remove(index);
        d.categoryIcons.remove(old);
        if (used > 0) {
            if (!d.categories.contains(FALLBACK)) d.categories.add(FALLBACK);
            for (Ledger.Entry e : d.entries)
                if (e.income && e.category.equals(old)) { e.category = FALLBACK; if (e.title.equals(old)) e.title = FALLBACK; }
        } else if (d.categories.isEmpty()) d.categories.add(FALLBACK);
        d.ensureIcons();
    }

    /** Moves one label to a new position; the order is the user-visible order everywhere. */
    static void move(Ledger.Data d, int from, int to) {
        List<String> list = d.categories;
        if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) throw new IllegalArgumentException("标签位置无效");
        if (from == to) return;
        list.add(to, list.remove(from));
    }

    private LabelRules() {}
}
