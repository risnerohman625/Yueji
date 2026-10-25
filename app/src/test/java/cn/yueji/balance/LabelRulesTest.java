package cn.yueji.balance;

import org.junit.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.Assert.*;

public class LabelRulesTest {
    Ledger.Data data() {
        Ledger.Data d = new Ledger.Data();
        d.ensureIcons();
        return d;
    }
    Ledger.Entry income(Ledger.Data d, long id, String category) {
        Ledger.Entry e = new Ledger.Entry(id, true, 10000, LocalDateTime.parse("2026-09-10T12:00"), category, "", 0);
        e.category = category;
        d.entries.add(e);
        return e;
    }
    interface Run { void go(); }
    void rejects(String fragment, Run run) {
        try { run.go(); fail("should reject: " + fragment); } catch (IllegalArgumentException e) { assertTrue(e.getMessage(), e.getMessage().contains(fragment)); }
    }
    void assertUnique(Ledger.Data d) {
        Set<String> seen = new HashSet<>();
        for (String c : d.categories) { String i = d.categoryIcons.get(c); assertNotNull(c, i); assertTrue(i, Ledger.ICON_SET.contains(i)); assertTrue("icon reused: " + i, seen.add(i)); }
        assertEquals(d.categories.size(), d.categoryIcons.size());
    }

    @Test public void defaultLabelsGetDistinctIcons() {
        Ledger.Data d = data();
        assertEquals("🎓", d.iconOf("奖学金"));
        assertUnique(d);
    }
    @Test public void addAppendsAndRejectsDuplicatesAndBadNames() {
        Ledger.Data d = data();
        LabelRules.add(d, "  家教  ", "💼");
        assertEquals("家教", d.categories.get(d.categories.size() - 1));
        assertEquals("💼", d.iconOf("家教"));
        assertUnique(d);
        rejects("已存在", () -> LabelRules.add(d, "家教", "🏆"));
        rejects("请填写", () -> LabelRules.add(d, "   ", "🏆"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 31; i++) longName.append('字');
        rejects("不能超过", () -> LabelRules.add(d, longName.toString(), "🏆"));
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < 30; i++) ok.append('字');
        LabelRules.add(d, ok.toString(), "🏆");
    }
    @Test public void iconsCannotBeSharedOrInvalid() {
        Ledger.Data d = data();
        String taken = d.iconOf("奖学金");
        rejects("奖学金", () -> LabelRules.add(d, "新标签", taken));
        rejects("请选择", () -> LabelRules.add(d, "新标签", null));
        rejects("请选择", () -> LabelRules.add(d, "新标签", "X"));
        assertNull(LabelRules.owner(d, taken, "奖学金"));
        assertEquals("奖学金", LabelRules.owner(d, taken, null));
        String free = LabelRules.firstFree(d, null);
        assertNotNull(free);
        assertNull(LabelRules.owner(d, free, null));
    }
    @Test public void libraryHoldsAsManyLabelsAsIcons() {
        Ledger.Data d = data();
        int i = 0;
        while (!LabelRules.full(d)) LabelRules.add(d, "标签" + (i++), LabelRules.firstFree(d, null));
        assertEquals(Ledger.ICONS.length, d.categories.size());
        assertUnique(d);
        assertNull("library exhausted", LabelRules.firstFree(d, null));
        rejects("已满", () -> LabelRules.add(d, "多出来的", Ledger.ICONS[0]));
    }
    @Test public void renameMovesEntriesTitleAndIconAndKeepsPosition() {
        Ledger.Data d = data();
        Ledger.Entry a = income(d, 1, "奖学金"), b = income(d, 2, "奖学金");
        b.title = "国家奖学金";
        int position = d.categories.indexOf("奖学金");
        String icon = d.iconOf("奖学金");
        LabelRules.edit(d, "奖学金", "校级奖学金", icon);
        assertEquals(position, d.categories.indexOf("校级奖学金"));
        assertFalse(d.categories.contains("奖学金"));
        assertEquals(icon, d.iconOf("校级奖学金"));
        assertNull(d.categoryIcons.get("奖学金"));
        assertEquals("校级奖学金", a.category);
        assertEquals("title equal to old name follows", "校级奖学金", a.title);
        assertEquals("校级奖学金", b.category);
        assertEquals("a custom title is kept", "国家奖学金", b.title);
        assertUnique(d);
    }
    @Test public void editCanKeepItsOwnIconAndChangeIt() {
        Ledger.Data d = data();
        String own = d.iconOf("生活费");
        LabelRules.edit(d, "生活费", "生活费", own);
        String free = LabelRules.firstFree(d, null);
        LabelRules.edit(d, "生活费", "生活费", free);
        assertEquals(free, d.iconOf("生活费"));
        assertNull("the old icon is released", LabelRules.owner(d, own, null));
        rejects("已被", () -> LabelRules.edit(d, "生活费", "生活费", d.iconOf("助研费")));
        rejects("已存在", () -> LabelRules.edit(d, "生活费", "助研费", free));
        rejects("不存在", () -> LabelRules.edit(d, "没有", "x", free));
    }
    @Test public void deleteMovesEntriesToOtherAndCreatesItWhenMissing() {
        Ledger.Data d = data();
        Ledger.Entry e = income(d, 1, "助研费");
        e.title = "助研费";
        LabelRules.delete(d, "助研费");
        assertFalse(d.categories.contains("助研费"));
        assertEquals("其他", e.category);
        assertEquals("其他", e.title);
        assertNull(d.categoryIcons.get("助研费"));
        assertUnique(d);

        Ledger.Data fresh = data();
        fresh.categories.remove("其他");
        fresh.ensureIcons();
        income(fresh, 5, "生活费");
        LabelRules.delete(fresh, "生活费");
        assertTrue(fresh.categories.contains("其他"));
        assertEquals("其他", fresh.entries.get(0).category);
        assertUnique(fresh);
    }
    @Test public void deleteWithoutRecordsJustRemoves() {
        Ledger.Data d = data();
        int before = d.categories.size();
        LabelRules.delete(d, "生活费");
        assertEquals(before - 1, d.categories.size());
        assertUnique(d);
    }
    @Test public void listNeverEndsUpEmpty() {
        Ledger.Data d = data();
        for (String name : new ArrayList<>(d.categories)) if (!name.equals("其他")) LabelRules.delete(d, name);
        assertEquals(Collections.singletonList("其他"), d.categories);
        rejects("至少保留", () -> LabelRules.delete(d, "其他"));

        Ledger.Data single = new Ledger.Data();
        single.categories.clear();
        single.categories.add("工资");
        single.ensureIcons();
        Ledger.Entry e = income(single, 1, "工资");
        LabelRules.delete(single, "工资");
        assertEquals(Collections.singletonList("其他"), single.categories);
        assertEquals("其他", e.category);
        assertUnique(single);

        Ledger.Data lone = new Ledger.Data();
        lone.categories.clear();
        lone.categories.add("工资");
        lone.ensureIcons();
        LabelRules.delete(lone, "工资");
        assertEquals(Collections.singletonList("其他"), lone.categories);
    }
    @Test public void otherCannotBeDeletedWhileItHoldsRecords() {
        Ledger.Data d = data();
        income(d, 1, "其他");
        rejects("不能删除", () -> LabelRules.delete(d, "其他"));
        assertTrue(d.categories.contains("其他"));
    }
    @Test public void moveReordersTheUserVisibleOrder() {
        Ledger.Data d = data();
        List<String> before = new ArrayList<>(d.categories);
        LabelRules.move(d, 0, 3);
        assertEquals(before.get(0), d.categories.get(3));
        assertEquals(before.get(1), d.categories.get(0));
        LabelRules.move(d, 3, 0);
        assertEquals(before, d.categories);
        LabelRules.move(d, 2, 2);
        assertEquals(before, d.categories);
        rejects("无效", () -> LabelRules.move(d, 0, 99));
        rejects("无效", () -> LabelRules.move(d, -1, 0));
        assertUnique(d);
    }
    @Test public void usageCountsOnlyIncomeOfThatLabel() {
        Ledger.Data d = data();
        income(d, 1, "奖学金");
        income(d, 2, "奖学金");
        income(d, 3, "生活费");
        Ledger.Entry large = new Ledger.Entry(4, false, 100, LocalDateTime.parse("2026-09-10T12:00"), "电脑", "", 0);
        large.kind = Ledger.LARGE;
        large.category = "奖学金";
        d.entries.add(large);
        assertEquals(2, LabelRules.usage(d, "奖学金"));
        assertEquals(1, LabelRules.usage(d, "生活费"));
        assertEquals(0, LabelRules.usage(d, "助研费"));
    }
}
