(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('CasesUISrv', function($q, localStorageService, Severity, Tlp) {
            var defaultFilter = {
                status: {
                    field: 'status',
                    value: [{
                        text: 'Open'
                    }],
                    filter: '(status:"Open")'
                }
            };

            var factory = {
                filterDefs: {
                    keyword: {
                        field: 'keyword',
                        type: 'string',
                        defaultValue: []
                    },
                    data: {
                        field: 'data',
                        type: 'string',
                        defaultValue: ''
                    },
                    status: {
                        field: 'status',
                        type: 'list',
                        defaultValue: []
                    },
                    severity: {
                        field: 'severity',
                        type: 'list',
                        defaultValue: [],
                        convert: function(value) {
                            // Convert the text value to its numeric representation
                            return Severity.keys[value];
                        }
                    },
                    resolutionStatus: {
                        field: 'resolutionStatus',
                        type: 'list',
                        defaultValue: []
                    },
                    tags: {
                        field: 'tags',
                        type: 'list',
                        defaultValue: []
                    },
                    owner: {
                        field: 'owner',
                        type: 'list',
                        defaultValue: []
                    },
                    tlp: {
                        field: 'tlp',
                        type: 'number',
                        defaultValue: null,
                        convert: function(value) {
                            // Convert the text value to its numeric representation
                            return Tlp.keys[value];
                        }
                    },
                    title: {
                        field: 'title',
                        type: 'string',
                        defaultValue: ''
                    },
                    startDate: {
                        field: 'startDate',
                        type: 'date',
                        defaultValue: {
                            from: null,
                            to: null
                        }
                    }
                },

                filters: {},
                activeFilters: {},
                context: {
                    state: null,
                    showFilters: false,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-flag', '-startDate']
                },
                currentState: null,
                currentPageSize: null,

                initContext: function(state) {
                    var storedContext = localStorageService.get('cases-section');
                    if (storedContext) {
                        factory.context = storedContext;
                        factory.filters = storedContext.filters || {};
                        factory.activeFilters = storedContext.activeFilters || {};
                        return;
                    } else {
                        factory.context = {
                            state: state,
                            showFilters: false,
                            showStats: false,
                            pageSize: 15,
                            sort: ['-flag', '-startDate']
                        };

                        factory.filters = defaultFilter;
                        factory.activeFilters = {};
                        factory.activeFilters.status = {
                            value: [{
                                text: 'Open'
                            }]
                        };

                        factory.storeContext();
                    }
                },

                initFilters: function() {
                    factory.activeFilters = {};
                    _.each(_.keys(factory.filterDefs), function(key) {
                        var def = factory.filterDefs[key];
                        factory.activeFilters[key] = {
                            field: def.field,
                            type: def.type,
                            value: factory.hasFilter(key) ? angular.copy(factory.getFilterValue(key)) : angular.copy(def.defaultValue)
                        };
                    });
                },

                isEmpty: function(value) {
                    return value === undefined || value === null || value.length === 0 || (angular.isObject(value) &&_.without(_.values(value), null, undefined, '').length === 0);
                },

                clearFilters: function() {
                    factory.filters = {};
                    factory.activeFilters = {};
                    factory.storeContext();
                    return $q.resolve({});
                },

                filter: function() {
                    var activeFilters = factory.activeFilters;

                    _.each(activeFilters, function(filterValue, field /*, filters*/ ) {
                        var value = filterValue.value;

                        if (!factory.isEmpty(value)) {
                            factory.addFilter(field, angular.copy(value));
                        } else {
                            factory.removeFilter(field);
                        }
                    });

                    factory.storeContext();

                    return $q.resolve();
                },

                addFilter: function(field, value) {
                    var query,
                        filterDef = factory.filterDefs[field],
                        convertFn = filterDef.convert || angular.identity;

                    // Prepare the filter value
                    if (field === 'keyword') {
                        query = value.replace(/"/gi, '\\"');
                    } else if (angular.isArray(value) && value.length > 0) {
                        query = _.map(value, function(val) {
                            return field + ':"' + convertFn(val.text.replace(/"/gi, '\\"')) + '"';
                        }).join(' OR ');
                        query = '(' + query + ')';
                    } else if (filterDef.type === 'date') {
                        var fromDate = value.from ? moment(value.from).hour(0).minutes(0).seconds(0).valueOf() : '*',
                            toDate = value.to ? moment(value.to).hour(23).minutes(59).seconds(59).valueOf() : '*';

                        query = field + ':[ ' + fromDate + ' TO ' + toDate + ' ]';

                    } else {
                        query = field + ':' + convertFn(value.replace(/"/gi, '\\"'));
                    }

                    factory.filters[field] = {
                        field: field,
                        value: value,
                        filter: query
                    };

                    return $q.resolve(factory.filters);
                },

                removeFilter: function(field) {
                    var filter = factory.activeFilters[field];

                    if(_.isObject(filter.value) && !_.isArray(filter.value)) {
                        _.each(filter.value, function(value, key) {
                            filter.value[key] = null;
                        });
                    }

                    delete factory.filters[field];
                    delete factory.activeFilters[field];

                    factory.storeContext();

                    return $q.resolve(factory.filters);
                },

                hasFilter: function(field) {
                    return factory.filters[field];
                },

                hasFilters: function() {
                    return _.keys(factory.filters).length > 0;
                },

                countFilters: function() {
                    return _.keys(factory.filters).length;
                },
                countSorts: function() {
                    return factory.context.sort.length;
                },

                getFilterValue: function(field) {
                    if (factory.filters[field]) {
                        return factory.filters[field].value;
                    }
                },

                buildQuery: function() {
                    if (_.keys(factory.filters).length === 0) {
                        return;
                    }

                    _.keys(factory.filters).map(function(key) {
                        return factory.filters[key].filter;
                    }).join(' AND ');

                    return _.pluck(factory.filters, 'filter').join(' AND ');
                },

                toggleStats: function() {
                    factory.context.showStats = !factory.context.showStats;
                    factory.storeContext();
                },

                toggleFilters: function() {
                    factory.context.showFilters = !factory.context.showFilters;
                    factory.storeContext();
                },

                setPageSize: function(pageSize) {
                    factory.context.pageSize = pageSize;
                    factory.storeContext();
                },

                setSort: function(sorts) {
                    factory.context.sort = sorts;
                    factory.storeContext();
                },

                storeContext: function() {
                    factory.context.filters = factory.filters;
                    factory.context.activeFilters = factory.activeFilters;
                    localStorageService.set('cases-section', factory.context);
                }
            };

            return factory;
        });
})();
