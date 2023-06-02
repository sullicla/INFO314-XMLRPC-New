package edu.uw.info314.xmlrpc.server;

public class Calc {
    public int add(int... args) {
        int result = 0;
        for (int arg : args) { result += arg; }
        return result;
    }
    public int subtract(int lhs, int rhs) { return lhs - rhs; }
    public int multiply(int... args) {
        int result = 0;
        for (int arg : args) { result *= arg; }
        return result;
    }
    public int divide(int lhs, int rhs) { return lhs / rhs; }
    public int modulo(int lhs, int rhs) { return lhs % rhs; }
}
