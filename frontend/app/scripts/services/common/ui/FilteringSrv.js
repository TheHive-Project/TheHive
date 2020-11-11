(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('FilteringSrv', function($q, DashboardSrv, QueryBuilderSrv, localStorageService) {
            return function(entity, sectionName, config) {
                var self = this;

                this.entity = entity;
                this.state = undefined;
                this.sectionName = sectionName;
                this.config = config;
                this.defaults = config.defaults || {};
                this.defaultFilter = config.defaultFilter || {};
                this.attributeKeys = [];

                this.initContext = function(state) {
                    self.state = state;
                    return DashboardSrv.getMetadata(this.config.version || 'v0')
                        .then(function(response) {
                            self.metadata = response;
                            self.attributes = angular.copy(response[self.entity].attributes);

                            _.each(self.config.excludes || [], function(exclude) {
                                delete self.attributes[exclude];
                            });

                            self.attributeKeys = _.keys(self.attributes).sort();
                        })
                        .then(function() {
                            var storedContext = localStorageService.get(self.sectionName);
                            if (storedContext && storedContext.state && storedContext.state === state) {
                                self.context = storedContext;
                                return;
                            } else {
                                self.context = {
                                    state: state,
                                    showFilters: self.defaults.showFilters || false,
                                    showStats: self.defaults.showStats || false,
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

                this.addFilter = function(filter) {
                    this.context.filters.push(filter || {
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

                this.resetContext = function() {
                    self.context = {
                        state: self.state,
                        showFilters: self.defaults.showFilters || false,
                        showStats: self.defaults.showStats || false,
                        pageSize: self.defaults.pageSize || 15,
                        sort: self.defaults.sort || [],
                        filters: self.defaultFilter || []
                    };

                    self.storeContext();
                };

                this.addFilterValue = function (field, value) {
                    var filterDef = self.attributes[field];

                    if(!filterDef) {
                        return;
                    }

                    var date,
                        type = filterDef.type,
                        filter = {
                            field: field,
                            type: filterDef.type
                        };

                    switch(type) {
                        case 'date':
                            date = moment(value);
                            filter.value = {
                                from: date.hour(0).minutes(0).seconds(0).toDate(),
                                to: date.hour(23).minutes(59).seconds(59).toDate()
                            };
                            break;
                        case 'tags':
                        case 'string':
                            filter.value = {
                                list: [{
                                    text: value,
                                    label: value
                                }]
                            };
                            break;
                        case 'number':
                        case 'enumeration':
                            if(!_.isArray(value)) {

                            }

                            filter.value = {
                                list: _.map(_.isArray(value) ? value : [value], function(item) {
                                    return {
                                        text: item,
                                        label: filterDef.labels[filterDef.values.indexOf(item)] || item
                                    };
                                })
                            };
                            break;
                        case 'boolean':
                            filter.value = value;
                            break;
                        case 'integer':
                        case 'float':
                            filter.value = {
                                operator: '=',
                                value: value
                            };
                            break;
                        case 'user':
                            break;
                    }

                    var pos = _.findIndex(this.context.filters, function(item) {
                        return item.field === field;
                    });

                    if(pos>-1) {
                        this.context.filters.splice(pos, 1);
                        this.context.filters.push(filter);
                    } else {
                        this.context.filters.push(filter);
                    }

                    return;
                };
            };
        });
})();
