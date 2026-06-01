package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.api.PaginationHelper;

class PaginationHelperTest {

    @Test
    void returnsAllItemsWhenPageAndSizeAreMissing() {
        var response = PaginationHelper.paginate(List.of("a", "b", "c"), null, null);

        assertEquals(List.of("a", "b", "c"), response.items());
        assertEquals(0, response.pageInfo().page());
        assertEquals(3, response.pageInfo().size());
        assertEquals(3L, response.pageInfo().totalItems());
        assertEquals(1, response.pageInfo().totalPages());
        assertFalse(response.pageInfo().hasNext());
    }

    @Test
    void returnsRequestedPageSlice() {
        var response = PaginationHelper.paginate(List.of("a", "b", "c", "d", "e"), 1, 2);

        assertEquals(List.of("c", "d"), response.items());
        assertEquals(1, response.pageInfo().page());
        assertEquals(2, response.pageInfo().size());
        assertEquals(5L, response.pageInfo().totalItems());
        assertEquals(3, response.pageInfo().totalPages());
        assertTrue(response.pageInfo().hasNext());
    }
}
