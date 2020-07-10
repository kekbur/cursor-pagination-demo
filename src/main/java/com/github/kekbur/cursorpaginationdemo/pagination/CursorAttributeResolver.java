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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.blazebit.persistence.DefaultKeyset;
import com.blazebit.persistence.DefaultKeysetPage;
import com.blazebit.persistence.Keyset;
import com.blazebit.persistence.KeysetPage;

public class CursorAttributeResolver implements HandlerMethodArgumentResolver
{
	private static final String BEFORE_PARAMETER_NAME = "before";
	private static final String AFTER_PARAMETER_NAME = "after";
	
	/**
	 * Supported keyset types.
	 */
	private static final List<Serializer<? extends Serializable>> KEYSET_TYPES = Collections.unmodifiableList(Arrays.asList(
    	new Serializer<Long>(Long.class, ByteBuffer::putLong, ByteBuffer::getLong),
    	new Serializer<Integer>(Integer.class, ByteBuffer::putInt, ByteBuffer::getInt)
    ));
	
	public CursorAttributeResolver() {}

	@Override
	public boolean supportsParameter(MethodParameter parameter)
	{
		return parameter.getParameterType().equals(Cursor.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception
	{
		CursorConfig config = parameter.getParameterAnnotation(CursorConfig.class);
		
		if (config == null)
		{
			throw new IllegalArgumentException("Method parameter has no @CursorConfig annotation: " + parameter.getAnnotatedElement());
		}
		
		if (config.sort().length == 0)
		{
			throw new IllegalArgumentException("@CursorConfig sort is empty: " + parameter.getAnnotatedElement());
		}
		
		Sort sort = buildSort(config.sort());
		String before = webRequest.getParameter(BEFORE_PARAMETER_NAME);
		String after = webRequest.getParameter(AFTER_PARAMETER_NAME);
		int perPage = config.maxResults();
		
		// magic values to get blaze-persistence to generate the right kind of SQL query
		int offset = before == null && after == null ? 0 : perPage;
		
		Keyset lowest = null;
		
		if (before != null)
		{
			lowest = new DefaultKeyset(cursorToKeyset(before));
		}
		
		Keyset highest = null;
		
		if (after != null)
		{
			highest = new DefaultKeyset(cursorToKeyset(after));
		}
		
		if (before != null && after != null)
		{
			throw new IllegalArgumentException("before and after are both non-null");
		}
		
		List<Keyset> keysets = Stream.of(lowest, highest)
			.filter(Objects::nonNull)
			.collect(toList());
		
		// magic values to get blaze-persistence to generate the right kind of SQL query
		int firstResult = before == null ? 0 : perPage * 2;
		KeysetPage keysetPage = new DefaultKeysetPage(firstResult, perPage, lowest, highest, keysets);
		Cursor cursor = new Cursor(keysetPage, sort, offset, perPage);
		
		return cursor;
	}
	
	private static Sort buildSort(Order[] input)
	{
		return Stream.of(input)
			.map(o -> new org.springframework.data.domain.Sort.Order(o.direction(), o.property()))
			.collect(collectingAndThen(toList(), Sort::by));
	}

	/**
	 * @param <T> the type that this serializer operates on
	 */
	private static class Serializer<T extends Serializable>
	{
		private final Class<T> clazz;
		private final BiConsumer<ByteBuffer, T> serializer;
		private final Function<ByteBuffer, T> deserializer;

		/**
		 * @param serializer serializer that writes objects of type T into a ByteBuffer
		 * @param deserializer deserializer that reads objects of type T from a ByteBuffer
		 */
		private Serializer(Class<T> clazz, BiConsumer<ByteBuffer, T> serializer, Function<ByteBuffer, T> deserializer)
		{
			this.clazz = clazz;
			this.serializer = serializer;
			this.deserializer = deserializer;
		}
		
		@SuppressWarnings("unchecked")
		private void serialize(ByteBuffer bb, Serializable value)
		{
			serializer.accept(bb, (T) value);
		}
	}
	
	/**
	 * Encodes the supplied keyset to a cursor string.
	 * @return the base64 encoded cursor
	 * @throws IllegalArgumentException if the keyset cannot be encoded
	 * @throws BufferOverflowException if the keyset is too large to be encoded
	 */
	static String keysetToCursor(Serializable[] keyset)
	{
		// TODO: remove the following line after https://github.com/Blazebit/blaze-persistence/issues/1128 is fixed
		Collections.reverse(Arrays.asList(keyset));
		ByteBuffer cursor = ByteBuffer.allocate(128);
		
		if (keyset.length > Byte.MAX_VALUE)
		{
			throw new IllegalArgumentException("Keyset length is greater than Byte.MAX_VALUE: " + keyset.length);
		}
		
		cursor.put((byte) keyset.length);
		
		for (Serializable value : keyset)
		{
			byte typeIndex = 0;
			
			for (Serializer<? extends Serializable> type : KEYSET_TYPES)
			{
				if (type.clazz.equals(value.getClass()))
				{
					cursor.put(typeIndex);
					type.serialize(cursor, value);
					break;
				}
				
				typeIndex++;
			}
			
			if (typeIndex >= KEYSET_TYPES.size())
			{
				throw new IllegalArgumentException("No matching keyset type - value=" + value + " - class=" + value.getClass() + " - Add it to the list KEYSET_TYPES if needed.");
			}
		}
		
		cursor.flip();
		
		ByteBuffer encoded = Base64.getUrlEncoder().encode(cursor);
		
		return new String(encoded.array(), StandardCharsets.ISO_8859_1);
	}
	
	/**
	 * Decodes the supplied base64 encoded cursor string as a keyset.
	 * @throws IllegalArgumentException if the cursor cannot be decoded
	 */
	private static Serializable[] cursorToKeyset(String base64Encoded)
	{
		ByteBuffer cursor = ByteBuffer.wrap(Base64.getUrlDecoder().decode(base64Encoded));
		byte size = cursor.get();
		
		if (size < 0)
		{
			throw new IllegalArgumentException("The keyset size is negative: " + size);
		}
		
		Serializable[] keyset = new Serializable[size];
		
		for (int i = 0; i < size; i++)
		{
			byte typeIndex = cursor.get();
			
			if (typeIndex > KEYSET_TYPES.size() || typeIndex < 0)
			{
				throw new IllegalArgumentException("Invalid keyset type index: " + typeIndex);
			}
			
			Function<ByteBuffer, ? extends Serializable> deserializer = KEYSET_TYPES.get(typeIndex).deserializer;
			
			try
			{
				keyset[i] = deserializer.apply(cursor);
			}
			catch (Exception e)
			{
				throw new IllegalArgumentException("The cursor is invalid at index: " + i, e);
			}
		}
		
		if (cursor.hasRemaining())
		{
			throw new IllegalArgumentException("The cursor has remaining bytes");
		}
		
		return keyset;
	}
}
