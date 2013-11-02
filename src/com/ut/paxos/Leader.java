package com.ut.paxos;

import java.util.*;

public class Leader extends Process {
    ProcessId[] acceptors;
    ProcessId[] replicas;
    BallotNumber ballot_number;
    boolean active = false;
    Map<Integer, Command> proposals = new HashMap<Integer, Command>();
    boolean isWaiting;

    private Monitor monitor;
    private HeartBeat heartBeat;

    private Set<ProcessId> deadProcesses;
    private ProcessId currentActiveLeader;

    //hearbeat variables
    private static final int heartbeatDelayMin = 1000;
    private static final int heartbeatDelayMax = 3000;

    boolean isIgnoring;

    public Leader(Env env, ProcessId me, ProcessId[] acceptors,
                  ProcessId[] replicas) {
        this.env = env;
        this.me = me;
        ballot_number = new BallotNumber(0, me);
        this.acceptors = acceptors;
        this.replicas = replicas;
        this.isIgnoring = false;
        this.isWaiting = false;
        env.addProc(me, this);
        this.deadProcesses = new HashSet<ProcessId>();
        this.currentActiveLeader = me;

    }

    public void body() {

        System.out.println("Here I am: " + me);

//		new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
//			me, acceptors, ballot_number);
        while (!isWaiting) {
            PaxosMessage msg = getNextMessage();

            if (msg instanceof HearBeatMessage) {
                HearBeatMessage m = (HearBeatMessage) msg;
                HearBeatMessageResponse hearBeatMessageResponse = new HearBeatMessageResponse(me);
                sendMessage(m.src, hearBeatMessageResponse);
                //System.err.println("heartbeat message received");
            } else if (msg instanceof HearBeatMessageResponse) {
                HearBeatMessageResponse m = (HearBeatMessageResponse) msg;
                if (monitor != null && monitor.getCurrent().equals(m.src)) {
                    monitor.resetTimeout();
                } else
                    System.err.println("Monitor process is not running");
                //we are cool keep waiting.
            } else if (msg instanceof ProposeMessage) {
                ProposeMessage m = (ProposeMessage) msg;
                if (!proposals.containsKey(m.slot_number)) {
                    proposals.put(m.slot_number, m.command);
                    if (active && !isIgnoring) {
                        new Commander(env,
                                new ProcessId("commander:" + me + ":" + ballot_number + ":" + m.slot_number),
                                me, acceptors, replicas, ballot_number, m.slot_number, m.command);
                    } else if (!isIgnoring) {
                        new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
                                me, acceptors, ballot_number);
                    }
                }
            } else if (msg instanceof AdoptedMessage) {

                AdoptedMessage m = (AdoptedMessage) msg;
                System.out.println("Adopted by " + m.src);
                if (ballot_number.equals(m.ballot_number)) {
                    Map<Integer, BallotNumber> max = new HashMap<Integer, BallotNumber>();
                    for (PValue pv : m.accepted) {
                        BallotNumber bn = max.get(pv.slot_number);
                        if (bn == null || bn.compareTo(pv.ballot_number) < 0) {
                            max.put(pv.slot_number, pv.ballot_number);
                            proposals.put(pv.slot_number, pv.command);
                        }
                    }

                    for (int sn : proposals.keySet()) {
                        System.err.println("I have " + proposals.size() + " proposals");
                        new Commander(env,
                                new ProcessId("commander:" + me + ":" + ballot_number + ":" + sn),
                                me, acceptors, replicas, ballot_number, sn, proposals.get(sn));
                    }
                    active = true;
                }
            } else if (msg instanceof PreemptedMessage) {
                PreemptedMessage m = (PreemptedMessage) msg;
                System.out.println(me + " preempted by leader " + m.ballot_number.getLeader_id());

                if (ballot_number.compareTo(m.ballot_number) < 0) {
                    ballot_number = new BallotNumber(m.ballot_number.round + 1, me);

                    if (!isIgnoring) {
                        setIgnoring(true);

                        if (monitor != null)
                            monitor.kill();

                        startMonitoring(m.ballot_number.getLeader_id());
                        continue;

                    } else if (deadProcesses.contains(m.ballot_number.getLeader_id())) {
                        new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
                                me, acceptors, ballot_number);
                        active = false;
                    }
                }
            } else {
                System.err.println("Leader: unknown msg type");
            }
        }
    }


    public void startMonitoring(ProcessId leader) {

        currentActiveLeader = leader;

        heartBeat = new HeartBeat(leader);
        heartBeat.start();

        monitor = new Monitor(System.currentTimeMillis(), this, currentActiveLeader);
        monitor.start();

    }

    class Monitor extends Thread {
        private long lastHeartBeat;
        private Leader parent;
        private ProcessId current;
        private boolean isRunning;

        Monitor(long lastHeartBeat, Leader parent, ProcessId current) {
            this.lastHeartBeat = lastHeartBeat;
            this.parent = parent;
            this.current = current;
            this.isRunning = true;

        }

        public void run() {
            System.err.println("Monitor started in " + parent.me);
            while (isRunning) {
                if (lastHeartBeat < System.currentTimeMillis() - 3000) {
                    parent.setIgnoring(false);
                    break;
                }
                yield();
            }
        }

        ProcessId getCurrent() {
            return current;
        }

        public void kill() {
            System.err.println("Monitor Killed");
            this.isRunning = false;
        }

        public void resetTimeout() {
            lastHeartBeat = System.currentTimeMillis();
        }

    }


    class HeartBeat extends Thread {

        private long lastHeartbeat;
        private ProcessId leader;
        Random rand = new Random();
        private boolean isRunning;

        public HeartBeat(ProcessId leader) {
            this.leader = leader;
            this.isRunning = true;
        }

        public void run() {
            int heartbeatDelay = rand.nextInt(heartbeatDelayMax - heartbeatDelayMin) + heartbeatDelayMin;
            while (isRunning) {
                if (heartbeatDelay < System.currentTimeMillis() - lastHeartbeat) {
                    lastHeartbeat = System.currentTimeMillis();
                    HearBeatMessage msg = new HearBeatMessage(me);
                    // System.err.println("Sending heartbeat");
                    sendMessage(leader, msg);
                    heartbeatDelay = rand.nextInt(heartbeatDelayMax - heartbeatDelayMin) + heartbeatDelayMin;
                }
                yield();
            }
        }

        public void kill() {
            this.isRunning = false;
        }

    }

    public void setWaiting(boolean waiting) {
        isWaiting = waiting;
    }

    public void getStatus() {
        System.out.println("Ignore " + isIgnoring + " | Waiting " + isWaiting + " | CurrentLeader " + currentActiveLeader);
    }

    public void setIgnoring(boolean ignoring) {
        System.err.println(me + " set IGNORING " + ignoring);

        isIgnoring = ignoring;
        if (!ignoring) {
            monitor.kill();
            heartBeat.kill();

            if (!currentActiveLeader.equals(me)) {
                deadProcesses.add(currentActiveLeader);
                currentActiveLeader = me;
            }
        }

    }
}


