(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('FilteringSrv', function($q, localStorageService, Severity, UiSettingsSrv) {
            return function(sectionName, config) {
                var self = this;

                this.sectionName = sectionName;
                this.config = config;
                this.defaults = config.defaults || {};
                this.filterDefs = config.filterDefs || {};
                this.defaultFilter = config.defaultFilter || {};

                this.filters = {};
                this.activeFilters = {};
                this.context = {
                    state: null,
                    showFilters: false,
                    showStats: false,
                    pageSize: this.defaults.pageSize || 15,
                    sort: this.defaults.sort || []
                };

                this.useAndForAlertTagsFilter = UiSettingsSrv.useAndForAlertTagsFilter();

                this.filterString = self.useAndForAlertTagsFilter ? ' AND ' : ' OR ';

                this.initContext = function(state) {
                    var storedContext = localStorageService.get(self.sectionName);
                    if (storedContext) {
                        self.context = storedContext;
                        self.filters = storedContext.filters || {};
                        self.activeFilters = storedContext.activeFilters || {};
                        return;
                    } else {
                        self.context = {
                            state: state,
                            showFilters: false,
                            showStats: false,
                            pageSize: self.defaults.pageSize || 15,
                            sort: self.defaults.sort || []
                        };

                        self.filters = self.defaultFilter;
                        self.activeFilters = _.mapObject(self.defaultFilter || {}, function(val){
                            return _.omit(val, 'field', 'filter');
                        });

                        self.storeContext();
                    }
                };

                this.initFilters = function() {
                    self.activeFilters = {};
                    _.each(_.keys(self.filterDefs), function(key) {
                        var def = self.filterDefs[key];
                        self.activeFilters[key] = {
                            field: def.field,
                            type: def.type,
                            value: self.hasFilter(key) ? angular.copy(self.getFilterValue(key)) : angular.copy(def.defaultValue)
                        };
                    });
                };

                this.isEmpty = function(value) {
                    return value === undefined || value === null || value.length === 0 || (angular.isObject(value) &&_.without(_.values(value), null, undefined, '').length === 0);
                };

                this.clearFilters = function() {
                    self.filters = {};
                    self.activeFilters = {};
                    self.storeContext();
                    return $q.resolve({});
                };

                this.filter = function() {
                    var activeFilters = self.activeFilters;

                    _.each(activeFilters, function(filterValue, field /*, filters*/ ) {
                        var value = filterValue.value;

                        if (!self.isEmpty(value)) {
                            self.addFilter(field, angular.copy(value));
                        } else {
                            self.removeFilter(field);
                        }
                    });

                    self.storeContext();

                    return $q.resolve();
                };

                self.addFilter = function(field, value) {
                    var query,
                        filterDef = self.filterDefs[field],
                        convertFn = filterDef.convert || angular.identity;

                    // Prepare the filter value
                    if (field === 'keyword') {
                        query = value;
                    } else if (angular.isArray(value) && value.length > 0) {
                        query = _.map(value, function(val) {
                            return field + ':"' + convertFn(val.text) + '"';
                        }).join(filterDef.type === 'tags' ? self.filterString : ' OR ');
                        query = '(' + query + ')';
                    } else if (filterDef.type === 'date') {
                        var fromDate = value.from ? moment(value.from).hour(0).minutes(0).seconds(0).valueOf() : '*',
                            toDate = value.to ? moment(value.to).hour(23).minutes(59).seconds(59).valueOf() : '*';

                        query = field + ':[ ' + fromDate + ' TO ' + toDate + ' ]';

                    } else {
                        query = field + ':' + convertFn(value);
                    }

                    self.filters[field] = {
                        field: field,
                        label: filterDef.label || field,
                        value: value,
                        filter: query
                    };

                    return $q.resolve(self.filters);
                };

                this.removeFilter = function(field) {
                    var filter = self.activeFilters[field];

                    if(_.isObject(filter.value) && !_.isArray(filter.value)) {
                        _.each(filter.value, function(value, key) {
                            filter.value[key] = null;
                        });
                    }

                    delete self.filters[field];
                    delete self.activeFilters[field];

                    self.storeContext();

                    return $q.resolve(self.filters);
                };

                this.hasFilter = function(field) {
                    return self.filters[field];
                };

                this.hasFilters = function() {
                    return _.keys(self.filters).length > 0;
                };

                this.countFilters = function() {
                    return _.keys(self.filters).length;
                };

                this.countSorts = function() {
                    return self.context.sort.length;
                };

                this.getFilterValue = function(field) {
                    if (self.filters[field]) {
                        return self.filters[field].value;
                    }
                };

                this.buildQuery = function() {
                    if (_.keys(self.filters).length === 0) {
                        return;
                    }

                    _.keys(self.filters).map(function(key) {
                        return self.filters[key].filter;
                    }).join(' AND ');

                    return _.pluck(self.filters, 'filter').join(' AND ');
                };

                this.toggleStats = function() {
                    self.context.showStats = !self.context.showStats;
                    self.storeContext();
                };

                this.toggleFilters = function() {
                    self.context.showFilters = !self.context.showFilters;
                    self.storeContext();
                };

                this.setPageSize = function(pageSize) {
                    self.context.pageSize = pageSize;
                    self.storeContext();
                };

                this.setSort = function(sorts) {
                    self.context.sort = sorts;
                    self.storeContext();
                };

                this.storeContext = function() {
                    self.context.filters = self.filters;
                    self.context.activeFilters = self.activeFilters;
                    localStorageService.set(self.sectionName, self.context);
                };

                this.getSeverities = function(query) {
                    var defer = $q.defer();

                    $q.resolve(_.map(Severity.keys, function(value, key) {
                        return {text: key};
                    })).then(function(response) {
                        var severities = [];

                        severities = _.filter(response, function(sev) {
                            var regex = new RegExp(query, 'gi');
                            return regex.test(sev.text);
                        });

                        defer.resolve(severities);
                    });

                    return defer.promise;
                };
            };
        });
})();
