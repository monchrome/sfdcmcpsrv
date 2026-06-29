package com.mb.sfdcmcp.sfdcmcpsrv.dto;

import java.util.List;

public class FieldDescribe {

    private String name;
    private String label;
    private String type;
    private boolean nillable;
    private boolean createable;
    private boolean updateable;
    private List<String> referenceTo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isNillable() { return nillable; }
    public void setNillable(boolean nillable) { this.nillable = nillable; }

    public boolean isCreateable() { return createable; }
    public void setCreateable(boolean createable) { this.createable = createable; }

    public boolean isUpdateable() { return updateable; }
    public void setUpdateable(boolean updateable) { this.updateable = updateable; }

    public List<String> getReferenceTo() { return referenceTo; }
    public void setReferenceTo(List<String> referenceTo) { this.referenceTo = referenceTo; }
}
