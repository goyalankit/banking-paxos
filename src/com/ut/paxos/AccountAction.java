package com.ut.paxos;

import java.io.PrintStream;

public class AccountAction {
    Account account;

    public AccountAction(Account account) {
        this.account = account;
    }

    public Account getAccount() {
        return account;
    }

    public String perform() {
        return "";
    }

    public void writeToConsole(String s, Boolean isError) {
        PrintStream out = isError ? System.err : System.out;
        //out.print("*** ");
      //  out.print(s);
       // out.println(" ***");
    }
}

class Withdraw extends AccountAction {
    private int wdamount;

    public Withdraw(Account account, int amount) {
        super(account);
        this.account = account;
        this.wdamount = amount;
    }

    @Override
    public String perform() {
        int currentBalance = account.getCurrentBalance();
        String response;
        if (currentBalance >= wdamount) {
            account.setCurrentBalance(currentBalance - wdamount);
            response = "; Withdraw Successful of $" + wdamount + " final balance " + account.getCurrentBalance();
            writeToConsole(response, false);
            return response;
        } else {
            response = "; Insufficient Balance in Source Account";
            writeToConsole(response, true);
            return response;
        }
    }

    int getWdamount() {
        return wdamount;
    }

    public String toString() {
        return "AccountAction(Withdraw(" + account + ", " + wdamount + "))";
    }
}

class Deposit extends AccountAction {
    private int dpamount;

    public Deposit(Account account, int amount) {
        super(account);
        this.account = account;
        this.dpamount = amount;
    }

    @Override
    public String perform() {
        String response;
        int currentBalance = account.getCurrentBalance();
        account.setCurrentBalance(currentBalance + dpamount);
        response = "; Deposit Successful of $" + dpamount + " final balance " + account.getCurrentBalance();
        writeToConsole(response, false);
        return response;
    }

    public String toString() {
        return "AccountAction(Deposit(" + account + ", " + dpamount + "))";
    }
}

class Transfer extends AccountAction {
    private int tamount;
    private Account dstaccount;

    public Transfer(Account srcaccount, Account dstaccount, int amount) {
        super(srcaccount);
        this.account = srcaccount;
        this.dstaccount = dstaccount;
        this.tamount = amount;
    }

    @Override
    public String perform() {
        int currentBalance = account.getCurrentBalance();
        String response;
        if (currentBalance >= tamount) {
            account.setCurrentBalance(currentBalance - tamount);
            dstaccount.setCurrentBalance(dstaccount.getCurrentBalance() + tamount);
            response = "; Transfer Successful of $" + tamount + " final balance " + account.getCurrentBalance();
            writeToConsole(response, false);
            return response;
        } else {
            response = "; Insufficient Balance in Source Account";
            writeToConsole(response, true);
            return response;
        }
    }

    public String toString() {
        return "AccountAction(Transfer(" + account + ", " + tamount + "))";
    }
}

class Query extends AccountAction {

    public Query(Account account) {
        super(account);
        this.account = account;
    }

    @Override
    public String perform() {
        String response = "; Query Successful Current balance for " + account.getAccountNo() + ": " + account.getCurrentBalance();
        writeToConsole(response, false);
        return response;
    }

    public String toString() {
        return "AccountAction(Query(" + account + "))";
    }
}
