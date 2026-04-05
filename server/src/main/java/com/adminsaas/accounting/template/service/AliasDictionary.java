package com.adminsaas.accounting.template.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AliasDictionary {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final String COLUMN_NAME = "\uD488\uBA85";
    private static final String COLUMN_SPEC = "\uADDC\uACA9";
    private static final String COLUMN_QUANTITY = "\uC218\uB7C9";
    private static final String COLUMN_UNIT = "\uB2E8\uC704";
    private static final String COLUMN_UNIT_PRICE = "\uB2E8\uAC00";
    private static final String COLUMN_AMOUNT = "\uAE08\uC561";

    private final Map<String, List<String>> labelAliases = Map.of(
            "title", List.of(LABEL_TITLE),
            "date", List.of(LABEL_DATE),
            "vendor", List.of(LABEL_VENDOR),
            "inspector", List.of(LABEL_INSPECTOR),
            "contractAmount", List.of(LABEL_CONTRACT_AMOUNT)
    );

    private final Map<String, List<String>> tableAliases = Map.of(
            "items", List.of(TABLE_ITEMS)
    );

    private final Map<String, List<String>> tableColumnAliases = Map.of(
            "items.name", List.of(COLUMN_NAME),
            "items.spec", List.of(COLUMN_SPEC),
            "items.quantity", List.of(COLUMN_QUANTITY),
            "items.unit", List.of(COLUMN_UNIT),
            "items.unitPrice", List.of(COLUMN_UNIT_PRICE),
            "items.amount", List.of(COLUMN_AMOUNT)
    );

    public Map<String, List<String>> labelAliases() {
        return labelAliases;
    }

    public Map<String, List<String>> tableAliases() {
        return tableAliases;
    }

    public Map<String, List<String>> tableColumnAliases() {
        return tableColumnAliases;
    }
}
