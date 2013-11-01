package com.ut.paxos;

public class AccountAction {
    Account account;

    public AccountAction(Account account) {
        this.account = account;
    }

    public Account getAccount() {
        return account;
    }

    public void perform(){
    }
}

class Withdraw extends AccountAction {
    private int wdamount;

    public Withdraw(Account account, int amount){
        super(account);
        this.account = account;
        this.wdamount = amount;
    }

    @Override
    public void perform(){
        int currentBalance = account.getCurrentBalance();
        if(currentBalance >= wdamount){
            account.setCurrentBalance(currentBalance - wdamount);
            System.out.println("Withdraw Successful of $"+wdamount + " final balance "+account.getCurrentBalance());
        }else{
            System.err.println("Insufficient Balance in Source Account");
        }
    }

    int getWdamount() {
        return wdamount;
    }

    public String toString(){
        return "AccountAction(Withdraw(" + account + ", " + wdamount + "))";
    }
}

class Deposit extends AccountAction {
    private int dpamount;
    public Deposit(Account account, int amount){
        super(account);
        this.account = account;
        this.dpamount = amount;
    }

    @Override
    public void perform(){
        int currentBalance = account.getCurrentBalance();

        account.setCurrentBalance(currentBalance + dpamount);
        System.out.println("Deposit Successful of $"+dpamount + " final balance "+account.getCurrentBalance());

    }

    public String toString(){
        return "AccountAction(Deposit(" + account + ", " + dpamount + "))";
    }
}

class Transfer extends AccountAction {
    private int tamount;
    private Account dstaccount;
    public Transfer(Account srcaccount, Account dstaccount, int amount){
        super(srcaccount);
        this.account = srcaccount;
        this.dstaccount = dstaccount;
        this.tamount = amount;
    }

    @Override
    public void perform(){
        int currentBalance = account.getCurrentBalance();
        if(currentBalance >= tamount ){
            account.setCurrentBalance(currentBalance - tamount);
            dstaccount.setCurrentBalance(dstaccount.getCurrentBalance() + tamount);
            System.out.println("Transfer Successful of $"+ tamount + " final balance "+account.getCurrentBalance());
        }else{
            System.err.println("Insufficient Balance in Source Account");
        }
    }

    public String toString(){
        return "AccountAction(Transfer(" + account + ", " + tamount + "))";
    }
}

class Query extends AccountAction {

    public Query(Account account){
        super(account);
        this.account = account;
    }

    @Override
    public void perform(){
        System.out.println("Query Successful Current balance for "+account.getAccountNo() + ": "+account.getCurrentBalance());
    }

    public String toString(){
        return "AccountAction(Query(" + account +"))";
    }
}
