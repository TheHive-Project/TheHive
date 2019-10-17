(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('FilteringSrv', function($q, DashboardSrv, QueryBuilderSrv, localStorageService, Severity) {
            return function(entity, sectionName, config) {
                var self = this;

                this.entity = entity;
                this.sectionName = sectionName;
                this.config = config;
                this.defaults = config.defaults || {};
                this.defaultFilter = config.defaultFilter || {};

                this.context = {
                    state: null,
                    showFilters: false,
                    showStats: false,
                    pageSize: this.defaults.pageSize || 15,
                    sort: this.defaults.sort || [],
                    filters: []
                };

                this.initContext = function(state) {

                    return DashboardSrv.getMetadata()
                        .then(function(response) {
                            self.metadata = response;
                            self.attributes = response[self.entity].attributes;
                        })
                        .then(function() {
                            var storedContext = localStorageService.get(self.sectionName);
                            if (storedContext) {
                                self.context = storedContext;
                                return;
                            } else {
                                self.context = {
                                    state: state,
                                    showFilters: false,
                                    showStats: false,
                                    pageSize: self.defaults.pageSize || 15,
                                    sort: self.defaults.sort || [],
                                    filters: self.defaultFilter || []
                                };

                                self.storeContext();
                            }
                        });
                };

                this.buildQuery = function() {
                    return QueryBuilderSrv.buildFiltersQuery(this.attributes, this.context.filters);
                };

                this.addFilter = function() {
                    this.context.filters.push({
                        field: null,
                        type: null
                    });
                };

                this.clearFilters = function() {
                    this.context.filters = [];
                    return $q.resolve();
                };

                this.removeFilter = function(index) {
                    this.context.filters.splice(index, 1);
                    return $q.resolve();
                };

                this.setFilterField = function(filter) {
                    var field = this.attributes[filter.field];

                    if (!field) {
                        return;
                    }

                    filter.type = field.type;

                    if (field.type === 'date') {
                        filter.value = {
                            from: null,
                            to: null
                        };
                    } else {
                        filter.value = null;
                    }
                };

                this.filterFields = function() {
                    return _.filter(this.attributes, function(value, key) {
                        return !key.startsWith('computed.');
                    });
                };

                this.countSorts = function() {
                    return self.context.sort.length;
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
                    localStorageService.set(self.sectionName, self.context);
                };

                this.getSeverities = function(query) {
                    var defer = $q.defer();

                    $q.resolve(_.map(Severity.keys, function(value, key) {
                        return {
                            text: key
                        };
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
