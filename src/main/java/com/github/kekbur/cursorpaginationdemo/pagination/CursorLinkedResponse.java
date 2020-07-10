/**
 * The MIT License
 * Copyright (c) 2020 kekbur
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.kekbur.cursorpaginationdemo.pagination;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import com.blazebit.persistence.spring.data.repository.KeysetAwarePage;

public class CursorLinkedResponse<T> extends ResponseEntity<Map<String, Object>>
{
	public CursorLinkedResponse(KeysetAwarePage<T> page)
	{
		super(buildResponseMap(page), HttpStatus.OK);
	}

	private static <T> Map<String, Object> buildResponseMap(KeysetAwarePage<T> page)
	{
		Map<String, Object> map = new LinkedHashMap<>();
		
		if (page.getPageable().getOffset() != 0 && page.getKeysetPage() != null && page.getKeysetPage().getLowest() != null && page.getNumberOfElements() > 0)
		{
			UriComponents previous = ServletUriComponentsBuilder.fromCurrentRequest()
				.replaceQueryParam("before", CursorAttributeResolver.keysetToCursor(page.getKeysetPage().getLowest().getTuple()))
				.replaceQueryParam("after")
				.build();
			map.put("previous", previous.toString());
		}
		
		if (page.getKeysetPage() != null && page.getKeysetPage().getHighest() != null && page.getNumberOfElements() >= page.getPageable().getPageSize())
		{
			UriComponents next = ServletUriComponentsBuilder.fromCurrentRequest()
				.replaceQueryParam("after", CursorAttributeResolver.keysetToCursor(page.getKeysetPage().getHighest().getTuple()))
				.replaceQueryParam("before")
				.build();
			map.put("next", next.toString());
		}
		
		map.put("content", page.getContent());
		
		return map;
	}
}
