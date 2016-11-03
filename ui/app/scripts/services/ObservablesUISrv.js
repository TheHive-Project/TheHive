(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ObservablesUISrv', function($q, localStorageService) {

            var factory = {
                actions: {
                    main: 'Action',
                    export: 'Export',
                    setIocFlog: 'Set IOC flog',
                    unsetIocFlog: 'Unset IOC flog',
                    changeTlp: 'Change TLP',
                    addTags: 'Add tags',
                    runAnalyzers: 'Run analyzers',
                    remove: 'Delete'
                },
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
                    dataType: {
                        field: 'dataType',
                        type: 'list',
                        defaultValue: []
                    },
                    tags: {
                        field: 'tags',
                        type: 'list',
                        defaultValue: []
                    },
                    ioc: {
                        field: 'ioc',
                        type: 'boolean',
                        defaultValue: null
                    },
                    tlp: {
                        field: 'tlp',
                        type: 'number',
                        defaultValue: null
                    },
                    message: {
                        field: 'description',
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
                    pageSize: 15
                },
                currentState: null,
                currentPageSize: null,

                initContext: function(state) {
                    if (!factory.context.state) {
                        var storedContext = localStorageService.get('observables-section');

                        if (storedContext && storedContext.state && storedContext.state === state) {
                            factory.context = storedContext;
                            factory.filters = storedContext.filters || {};
                            factory.activeFilters = storedContext.activeFilters || {};

                            console.log('Init Observables from localstorage');
                            return;
                        }
                    }

                    if (state !== factory.context.state) {
                        factory.context = {
                            state: state,
                            showFilters: false,
                            showStats: false,
                            pageSize: 15
                        };

                        factory.filters = {};
                        factory.activeFilters = {};

                        factory.storeContext();

                        console.log('Init Observables context');
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
                        filterDef = factory.filterDefs[field];

                    // Prepare the filter value
                    /*
                    if(factory.hasFilter(field)) {
                        var oldValue = factory.getFilterValue(field);
                        console.log('Filter ['+field+'] already exists = ' + oldValue);

                        if(factory.filterDefs[field].type === 'list') {
                            value = angular.isArray(oldValue) ? oldValue.push({text: value}) : [{text: oldValue}, {text: value}];
                        }
                    }
                    */

                    if (field === 'keyword') {
                        query = value;
                    } else if (angular.isArray(value) && value.length > 0) {
                        query = _.map(value, function(val) {
                            return field + ':"' + val.text + '"';
                        }).join(' OR ');
                        query = '(' + query + ')';
                    } else if (filterDef.type === 'date') {
                        var fromDate = value.from ? moment(value.from).hour(0).minutes(0).seconds(0).valueOf() : '*',
                            toDate = value.to ? moment(value.to).hour(23).minutes(59).seconds(59).valueOf() : '*';

                        query = field + ':[ ' + fromDate + ' TO ' + toDate + ' ]';

                    } else {
                        query = field + ':' + value;
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

                    if(angular.isObject(filter.value)) {
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

                storeContext: function() {
                    factory.context.filters = factory.filters;
                    factory.context.activeFilters = factory.activeFilters;
                    localStorageService.set('observables-section', factory.context);
                }
            };

            return factory;
        });
})();
