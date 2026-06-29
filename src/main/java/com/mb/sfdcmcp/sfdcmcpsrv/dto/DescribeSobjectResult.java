package com.mb.sfdcmcp.sfdcmcpsrv.dto;

import java.util.List;

public class DescribeSobjectResult {

    private String name;
    private String label;
    private boolean createable;
    private boolean updateable;
    private boolean queryable;
    private List<FieldDescribe> fields;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isCreateable() { return createable; }
    public void setCreateable(boolean createable) { this.createable = createable; }

    public boolean isUpdateable() { return updateable; }
    public void setUpdateable(boolean updateable) { this.updateable = updateable; }

    public boolean isQueryable() { return queryable; }
    public void setQueryable(boolean queryable) { this.queryable = queryable; }

    public List<FieldDescribe> getFields() { return fields; }
    public void setFields(List<FieldDescribe> fields) { this.fields = fields; }
}