/*
 * Copyright 2014 - 2020 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.kekbur.cursorpaginationdemo.controller;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blazebit.persistence.spring.data.repository.KeysetAwarePage;
import com.blazebit.text.FormatUtils;
import com.blazebit.text.ParserContext;
import com.blazebit.text.SerializableFormat;
import com.github.kekbur.cursorpaginationdemo.filter.Filter;
import com.github.kekbur.cursorpaginationdemo.model.Cat;
import com.github.kekbur.cursorpaginationdemo.pagination.Cursor;
import com.github.kekbur.cursorpaginationdemo.pagination.CursorConfig;
import com.github.kekbur.cursorpaginationdemo.pagination.CursorLinkedResponse;
import com.github.kekbur.cursorpaginationdemo.pagination.Order;
import com.github.kekbur.cursorpaginationdemo.repository.CatRepository;

@RestController
public class CatController
{
	private static final Map<String, SerializableFormat<?>> FILTER_ATTRIBUTES;
	
	static
	{
		Map<String, SerializableFormat<?>> filterAttributes = new HashMap<>();
		filterAttributes.put("id", FormatUtils.getAvailableFormatters().get(Long.class));
		filterAttributes.put("name", FormatUtils.getAvailableFormatters().get(String.class));
		filterAttributes.put("age", FormatUtils.getAvailableFormatters().get(Integer.class));
		filterAttributes.put("owner.name", FormatUtils.getAvailableFormatters().get(String.class));
		FILTER_ATTRIBUTES = Collections.unmodifiableMap(filterAttributes);
	}
	
	@Autowired
	private CatRepository catRepository;
	
	@RequestMapping(path = "/all-cats", method = RequestMethod.GET)
	public List<Cat> findAll()
	{
		Sort sort = Sort.by(Direction.ASC, "age")
			.and(Sort.by(Direction.DESC, "id"));
		
		return catRepository.findAll(sort);
	}
	
	@RequestMapping(path = "/cats", method = RequestMethod.GET)
	public CursorLinkedResponse<Cat> findPaginated
	(
		@CursorConfig(maxResults = 10, sort = { @Order(property = "age", direction = Direction.ASC), @Order(property = "id", direction = Direction.DESC) })
		Cursor cursor,
		
		@RequestParam(name = "filter", required = false)
		Filter[] filter
	)
	{
		Specification<Cat> specification = getSpecificationForFilter(filter);
		
		KeysetAwarePage<Cat> page = catRepository.findAll(specification, cursor);
		
		return new CursorLinkedResponse<>(page);
	}
	
	@SuppressWarnings({"serial", "unchecked", "rawtypes"})
	private Specification<Cat> getSpecificationForFilter(final Filter[] filter) {
		if (filter == null || filter.length == 0) {
			return null;
		}
		return new Specification<Cat>() {
			@Override
			public Predicate toPredicate(Root<Cat> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
				List<Predicate> predicates = new ArrayList<>();
				ParserContext parserContext = new ParserContextImpl();
				try {
					for (Filter f : filter) {
						SerializableFormat<?> format = FILTER_ATTRIBUTES.get(f.getField());
						if (format != null) {
							String[] fieldParts = f.getField().split("\\.");
							Path<?> path = root.get(fieldParts[0]);
							for (int i = 1; i < fieldParts.length; i++) {
								path = path.get(fieldParts[i]);
							}
							switch (f.getKind()) {
								case EQ:
									predicates.add(criteriaBuilder.equal(path, format.parse(f.getValue(), parserContext)));
									break;
								case GT:
									predicates.add(criteriaBuilder.greaterThan((Expression<Comparable>) path, (Comparable) format.parse(f.getValue(), parserContext)));
									break;
								case LT:
									predicates.add(criteriaBuilder.lessThan((Expression<Comparable>) path, (Comparable) format.parse(f.getValue(), parserContext)));
									break;
								case GTE:
									predicates.add(criteriaBuilder.greaterThanOrEqualTo((Expression<Comparable>) path, (Comparable) format.parse(f.getValue(), parserContext)));
									break;
								case LTE:
									predicates.add(criteriaBuilder.lessThanOrEqualTo((Expression<Comparable>) path, (Comparable) format.parse(f.getValue(), parserContext)));
									break;
								case IN:
									List<String> values = f.getValues();
									List<Object> filterValues = new ArrayList<>(values.size());
									for (String value : values) {
										filterValues.add(format.parse(value, parserContext));
									}
									predicates.add(path.in(filterValues));
									break;
								case BETWEEN:
									predicates.add(criteriaBuilder.between((Expression<Comparable>) path, (Comparable) format.parse(f.getLow(), parserContext), (Comparable) format.parse(f.getHigh(), parserContext)));
									break;
								case STARTS_WITH:
									predicates.add(criteriaBuilder.like((Expression<String>) path, format.parse(f.getValue(), parserContext) + "%"));
									break;
								case ENDS_WITH:
									predicates.add(criteriaBuilder.like((Expression<String>) path, "%" + format.parse(f.getValue(), parserContext)));
									break;
								case CONTAINS:
									predicates.add(criteriaBuilder.like((Expression<String>) path, "%" + format.parse(f.getValue(), parserContext) + "%"));
									break;
								default:
									throw new UnsupportedOperationException("Unsupported kind: " + f.getKind());
							}
						}
					}
				} catch (ParseException ex) {
					throw new RuntimeException(ex);
				}
				return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
			}
		};
	}

	private static class ParserContextImpl implements ParserContext {
		private final Map<String, Object> contextMap;

		@SuppressWarnings({"unchecked", "rawtypes"})
		private ParserContextImpl() {
			this.contextMap = new HashMap();
		}

		public Object getAttribute(String name) {
			return this.contextMap.get(name);
		}

		@SuppressWarnings("unused")
		public void setAttribute(String name, Object value) {
			this.contextMap.put(name, value);
		}
	}
}
