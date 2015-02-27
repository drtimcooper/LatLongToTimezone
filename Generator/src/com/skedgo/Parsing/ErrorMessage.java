package com.skedgo.Parsing;


public class ErrorMessage extends Throwable {
    public String message;

    public ErrorMessage(String s)
    {
        message = s;
    }

    public String toString()
    {
        return message;
    }
}
