(function() {
    'use strict';
    angular.module('theHiveServices').service('QueryBuilderSrv', function() {
        var self = this;

        this._buildQueryFromDefaultFilter = function(fieldDef, filter) {
            return {
                _field: filter.field,
                _value: filter.value
            };
        };

        this._buildQueryFromBooleanFilter = function(fieldDef, filter) {
            if(filter.value === null) {
                return undefined;
            }

            return {
                _field: filter.field,
                _value: filter.value
            };
        };

        this._buildQueryFromNumberFilter = function(fieldDef, filter) {
            if (!filter || !filter.value) {
                return null;
            }
            var operator = filter.value.operator || 'eq';
            var criterion = {};
            criterion[filter.field] = filter.value.value;

            switch(operator) {
                case '<':
                    return {'_lt': criterion};
                case '<=':
                    return {'_lte': criterion};
                case '>':
                    return {'_gt': criterion};
                case '>=':
                    return {'_gte': criterion};
                case '!=':
                    return {'_not': criterion};
                default:
                    return {'_field': filter.field, '_value': filter.value.value};
            }
        };

        this._buildQueryFromListFilter = function(fieldDef, filter) {
            if (!filter || !filter.value) {
                return null;
            }
            var operator = filter.value.operator || 'any';
            var values = _.pluck(filter.value.list, 'text');

            if(values.length > 0) {
                var criterions = _.map(values, function(val) {
                    //return {_string: filter.field + ':' + val};
                    return {_field: filter.field, _value: val};
                });

                var criteria = {};
                switch(operator) {
                    case 'all':
                        criteria = criterions.length === 1 ? criterions[0] : { _and: criterions };
                        break;
                    case 'none':
                        criteria = {
                            _not: criterions.length === 1 ? criterions[0] : { _or: criterions }
                        };
                        break;
                    //case 'any':
                    default:
                        criteria = criterions.length === 1 ? criterions[0] : { _or: criterions };
                }

                return criteria;
            }

            return null;
        };

        this._buildQueryFromDateFilter = function(fieldDef, filter) {
            var value = filter.value,
                start,
                end;

            if(value.from && value.from !== null) {
                start = _.isString(value.from) ? (new Date(value.from)).getTime() : value.from.getTime();
            } else {
                start = null;
            }

            if(value.to && value.to !== null) {
                end = _.isString(value.to) ? (new Date(value.to)).setHours(23, 59, 59, 999) : value.to.getTime();
            } else {
                end = null;
            }

            if (start !== null && end !== null) {
                return {
                    _between: { _field: filter.field, _from: start, _to: end }
                };
            } else if (start !== null) {
                var gt = {};
                gt[filter.field] = start;

                return {
                    _gt: gt
                };
            } else {
                var lt = {};
                lt[filter.field] = end;

                return {
                    _lt: lt
                };
            }

            return null;
        };

        this._buildQueryFromFilter = function(fieldDef, filter) {
            if (filter.type === 'date') {
                return this._buildQueryFromDateFilter(fieldDef, filter);
            } else if(filter.type === 'boolean') {
                return this._buildQueryFromBooleanFilter(fieldDef, filter);
            } else if(filter.value.list || filter.type === 'user' || filter.field === 'tags' || filter.type === 'enumeration' || fieldDef.values.length > 0) {
                return this._buildQueryFromListFilter(fieldDef, filter);
            } else if(filter.type === 'number') {
                return this._buildQueryFromNumberFilter(fieldDef, filter);
            }
            return {
                _string: filter.field + ':"' + filter.value +'"'
            };
        };

        this.buildFiltersQuery = function(fields, filters) {
            var criterias =
                _.map(filters, function(filter) {
                    return self._buildQueryFromFilter(fields[filter.field], filter);
                }) || [];

            criterias = _.without(criterias, null, undefined, {});

            return criterias.length === 0 ? {} : criterias.length === 1 ? criterias[0] : { _and: criterias };
        };
    });
})();
