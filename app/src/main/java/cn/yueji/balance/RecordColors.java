package cn.yueji.balance;

final class RecordColors {
    enum Role { INCOME, EXPENSE, REIMBURSE, INVESTMENT, BALANCE }
    static Role entry(String kind){return Ledger.INCOME.equals(kind)?Role.INCOME:Ledger.REIMBURSE.equals(kind)?Role.REIMBURSE:Ledger.LARGE.equals(kind)?Role.EXPENSE:Role.INVESTMENT;}
    static Role flow(Ledger.Data data,AccountCash.Flow flow){
        if(flow.key.startsWith("receipt:"))return Role.REIMBURSE;
        if(flow.key.startsWith("entry:"))for(Ledger.Entry e:data.entries)if(e.id==flow.entryId)return entry(e.kind);
        if(flow.transfer)return flow.delta>=0?Role.INCOME:Role.EXPENSE;
        return Role.INVESTMENT;
    }
    static int color(Role role){switch(role){case INCOME:return Ui.RED;case EXPENSE:return Ui.GREEN;case REIMBURSE:return Ui.BLUE;case INVESTMENT:return Ui.ORANGE;default:return Ui.RECORD_PURPLE;}}
    private RecordColors(){}
}
