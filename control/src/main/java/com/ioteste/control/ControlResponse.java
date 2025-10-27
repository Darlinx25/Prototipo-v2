package com.ioteste.control;

import java.util.List;

public class ControlResponse {
    private List<Operation> operations;
    private Context context;

    public ControlResponse(List<Operation> operations, Context context) {
        this.operations = operations;
        this.context = context;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public Context getContext() {
        return context;
    }
}
