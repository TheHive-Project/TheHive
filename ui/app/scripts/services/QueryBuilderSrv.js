(function() {
    'use strict';
    angular.module('theHiveServices').service('QueryBuilderSrv', function() {
        var self = this;

        this._buildQueryFromDefaultFilter = function(fieldDef, filter) {
            return {
                _field: filter.field,
                _value: filter.value
            };
        }

        this._buildQueryFromListFilter = function(fieldDef, filter) {
            if (!filter || !filter.value) {
                return null;
            }
            var operator = filter.value.operator || 'any';
            var values = _.pluck(filter.value.list, 'text');

            if(values.length > 0) {
                var criterions = _.map(values, function(val) {
                    // var o = {};
                    // o[filter.field] = val;
                    // return o;
                    return {_string: filter.field + ':' + val};
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
                    case 'any':
                    default:
                        criteria = criterions.length === 1 ? criterions[0] : { _or: criterions };
                }

                return criteria;
            }

            return null;
        };

        this._buildQueryFromDateFilter = function(fieldDef, filter) {
            var value = filter.value;

            var start = value.from && value.from != null ? value.from.getTime() : null;
            var end = value.to && value.to != null ? value.to.setHours(23, 59, 59, 999) : null;

            if (start !== null && end !== null) {
                return {
                    _between: { _field: filter.field, _from: start, _to: end }
                };
            } else if (start !== null) {
                return {
                    _gt: { _field: filter.field, _value: start }
                };
            } else {
                return {
                    _lt: { _field: filter.field, _value: end }
                };
            }

            return null;
        };

        this._buildQueryFromFilter = function(fieldDef, filter) {
            if (filter.type === 'date') {
                return this._buildQueryFromDateFilter(fieldDef, filter);
            } else if(filter.type === 'user' || filter.field === 'tags' || filter.type === 'enumeration' || fieldDef.values.length > 0) {
                return this._buildQueryFromListFilter(fieldDef, filter);
            } else if(filter.type === 'boolean' || filter.type === 'number') {
                return this._buildQueryFromDefaultFilter(fieldDef, filter);
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

            criterias = _.without(criterias, null, undefined);

            return criterias.length === 0 ? {} : criterias.length === 1 ? criterias[0] : { _and: criterias };
        };
    });
})();
