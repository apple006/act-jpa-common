package act.db.jpa;

/*-
 * #%L
 * ACT JPA Common Module
 * %%
 * Copyright (C) 2018 ActFramework
 * %%
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
 * #L%
 */

import static act.Act.app;
import static act.db.jpa.sql.SQL.Type.*;

import act.app.DbServiceManager;
import act.db.DB;
import act.db.DaoBase;
import act.db.Model;
import act.db.jpa.sql.SQL;
import org.osgl.$;
import org.osgl.util.E;
import org.osgl.util.S;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.persistence.EntityManager;

public class JPADao<ID_TYPE, MODEL_TYPE> extends DaoBase<ID_TYPE, MODEL_TYPE, JPAQuery<MODEL_TYPE>> {

    private volatile JPAService _jpa;
    protected String entityName;
    protected String createdColumn;
    protected String lastModifiedColumn;
    protected String idColumn;
    protected Field idField;
    private String qIdList;

    public JPADao(Class<ID_TYPE> idType, Class<MODEL_TYPE> modelType, JPAService jpa) {
        super(idType, modelType);
        setJPAService(jpa);
    }

    public JPADao() {
    }

    public JPAService jpa() {
        if (null != _jpa) {
            return _jpa;
        }
        synchronized (this) {
            if (null == _jpa) {
                DB db = modelType().getAnnotation(DB.class);
                String dbId = null == db ? DbServiceManager.DEFAULT : db.value();
                _jpa = app().dbServiceManager().dbService(dbId);
            }
        }
        return _jpa;
    }

    @Override
    public MODEL_TYPE findById(ID_TYPE id) {
        return em().find(modelClass, id);
    }

    @Override
    public MODEL_TYPE findLatest() {
        E.unsupportedIf(null == createdColumn, "no CreatedAt column defined");
        return q().orderBy(createdColumn).first();
    }

    @Override
    public MODEL_TYPE findLastModified() {
        E.unsupportedIf(null == lastModifiedColumn, "no LastModifiedAt column defined");
        return q().orderBy(lastModifiedColumn).first();
    }

    @Override
    public Iterable<MODEL_TYPE> findBy(String expression, Object... values) throws IllegalArgumentException {
        return q(expression, values).fetch();
    }

    @Override
    public MODEL_TYPE findOneBy(String expression, Object... values) throws IllegalArgumentException {
        return q(expression, values).first();
    }

    @Override
    public Iterable<MODEL_TYPE> findByIdList(Collection<ID_TYPE> idList) {
        return q(qIdList, idList).fetch();
    }

    @Override
    public MODEL_TYPE reload(MODEL_TYPE entity) {
        em().refresh(entity);
        return entity;
    }

    @Override
    public ID_TYPE getId(MODEL_TYPE entity) {
        if (entity instanceof Model) {
            return $.cast(((Model) entity)._id());
        }
        return null == idField ? null : (ID_TYPE) $.getFieldValue(entity, idField);
    }

    @Override
    public long countBy(String expression, Object... values) throws IllegalArgumentException {
        return q(expression, values).count();
    }

    @Override
    public MODEL_TYPE save(MODEL_TYPE entity) {
        EntityManager em = em();
        if (!em.contains(entity)) {
            em.persist(entity);
        } else {
            em.merge(entity);
        }
        return entity;
    }

    @Override
    public void save(MODEL_TYPE entity, String fieldList, Object... values) {
        values = $.concat(values, getId(entity));
        JPAQuery<MODEL_TYPE> q = createUpdateQuery(fieldList, idColumn, values);
        q.executeUpdate();
        em().flush();
    }

    @Override
    public List<MODEL_TYPE> save(Iterable<MODEL_TYPE> entities) {
        List<MODEL_TYPE> list = new ArrayList<>();
        int count = 0;
        EntityManager em = em();
        for (MODEL_TYPE entity : entities) {
            em.persist(entity);
            // TODO: make `20` configurable
            if (++count % 20 == 0) {
                em.flush();
                em.clear();
            }
            list.add(entity);
        }
        return list;
    }

    @Override
    public void delete(MODEL_TYPE entity) {
        EntityManager em = em();
        em.remove(entity);
        em.flush();
    }

    @Override
    public void delete(JPAQuery<MODEL_TYPE> query) {
        query = query.asDelete();
        query.executeUpdate();
        em().flush();
    }

    @Override
    public void deleteById(ID_TYPE id) {
        delete(q(DELETE, idColumn, id));
    }

    @Override
    public void deleteBy(String expression, Object... values) throws IllegalArgumentException {
        delete(q(DELETE, expression, values));
    }

    @Override
    public void deleteAll() {
        delete(q(DELETE));
    }

    @Override
    public void drop() {
        deleteAll();
    }

    @Override
    public JPAQuery<MODEL_TYPE> q() {
        return q(FIND);
    }

    public JPAQuery<MODEL_TYPE> q(SQL.Type type) {
        return q(type, "");
    }

    @Override
    public JPAQuery<MODEL_TYPE> createQuery() {
        return q();
    }

    @Override
    public JPAQuery<MODEL_TYPE> q(String expression, Object... values) {
        return q(FIND, expression, values);
    }

    public JPAQuery<MODEL_TYPE> q(SQL.Type type, String expression, Object... values) {
        E.unsupportedIf(SQL.Type.UPDATE == type, "UPDATE not supported in q() API");
        JPAQuery<MODEL_TYPE> q = new JPAQuery<>(jpa(), em(), modelClass, type, expression);
        int len = values.length;
        for (int i = 0; i < len; ++i) {
            q.setParameter(i + 1, values[i]);
        }
        return q;
    }

    @Override
    public JPAQuery<MODEL_TYPE> createQuery(String expression, Object... values) {
        return q(expression, values);
    }

    public JPAQuery<MODEL_TYPE> createFindQuery(String expression, Object... values) {
        return q(FIND, expression, values);
    }

    public JPAQuery<?> createFindQuery(String fieldList, String expression, Object... values) {
        String[] columns = fieldList.split(S.COMMON_SEP);
        JPAQuery<?> q = new JPAQuery<>(jpa(), em(), modelClass, SQL.Type.FIND, expression, columns);
        int len = values.length;
        for (int i = 0; i < len; ++i) {
            q.setParameter(i + 1, values[i]);
        }
        return q;
    }

    public JPAQuery<MODEL_TYPE> createDeleteQuery(String expression, Object... values) {
        return q(DELETE, expression, values);
    }

    public JPAQuery<MODEL_TYPE> createUpdateQuery(String fieldList, String expression, Object... values) {
        String[] columns = fieldList.split(S.COMMON_SEP);
        JPAQuery<MODEL_TYPE> q = new JPAQuery<>(jpa(), em(), modelClass, SQL.Type.UPDATE, expression, columns);
        int len = values.length;
        for (int i = 0; i < len; ++i) {
            q.setParameter(i + 1, values[i]);
        }
        return q;
    }

    public JPAQuery<MODEL_TYPE> createCountQuery(String expression, Object... values) {
        return q(COUNT, expression, values);
    }

    void setJPAService(JPAService jpa) {
        Class<?> modelType = modelType();
        this.entityName = jpa.entityName(modelType);
        this.createdColumn = jpa.createdColumn(modelType);
        this.lastModifiedColumn = jpa.lastModifiedColumn(modelType);
        this.idColumn = jpa.idColumn(modelType);
        this.qIdList = S.fmt("%s in ", idColumn);
        this.idField = jpa.idField(modelType);
        this._jpa = jpa;
    }

    private EntityManager em() {
        return JPAContext.em(jpa());
    }
}
