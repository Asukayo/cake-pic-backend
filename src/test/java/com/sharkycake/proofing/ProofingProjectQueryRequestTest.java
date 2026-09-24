package com.sharkycake.proofing;

import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProofingProjectQueryRequestTest {

    @Test
    void bindsDocumentedPageParameter() {
        ProofingProjectQueryRequest request = new ProofingProjectQueryRequest();
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("spaceId", "7");
        values.add("page", "2");
        values.add("pageSize", "50");

        WebDataBinder binder = new WebDataBinder(request);
        binder.bind(values);

        assertFalse(binder.getBindingResult().hasErrors());
        assertEquals(7L, request.getSpaceId());
        assertEquals(2, request.getCurrent());
        assertEquals(50, request.getPageSize());
    }
}
