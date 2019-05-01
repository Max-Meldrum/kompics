/*
 * This file is part of the Kompics component model runtime.
 * <p>
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS)
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package se.sics.kompics;

/**
 *
 * @author Lars Kroll {@literal <lkroll@kth.se>}
 */
public abstract class ClassMatchedHandler<V, E extends KompicsEvent & PatternExtractor<Class<Object>, ? super V>>
        extends MatchedHandler<Class<Object>, V, E> {

    private Class<Object> matchType = null;

    protected ClassMatchedHandler() {
    }

    @SuppressWarnings("unchecked")
    protected ClassMatchedHandler(Class<?> matchType) {
        super();
        this.matchType = (Class<Object>) matchType; // just cast away the generics
    }

    @SuppressWarnings("unchecked")
    protected ClassMatchedHandler(Class<E> cxtType, Class<?> matchType) {
        super(cxtType);
        this.matchType = (Class<Object>) matchType; // just cast away the generics
    }

    @Override
    public Class<Object> pattern() {
        return this.matchType;
    }

    void setPattern(Class<Object> matchType) {
        this.matchType = matchType;
    }
}
