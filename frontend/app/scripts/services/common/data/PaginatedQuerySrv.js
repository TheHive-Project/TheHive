(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('PaginatedQuerySrv', function($filter, StreamSrv, QuerySrv) {

            return function(options) {
                var self = this;

                // Internal fields
                this.values = [];
                this.allValues = [];
                this.total = 0;
                this.currentPage = 1;
                this.filterHash = null;

                // Save options
                this.options = options;
                this.root = options.root || 'any';
                this.objectType = options.objectType;
                this.version = options.version;
                this.scope = options.scope;
                this.loadAll = !!options.loadAll;
                this.pageSize = options.pageSize || 10;
                this.pageOptions = options.pageOptions || {};
                this.baseFilter = options.baseFilter;
                this.filter = options.filter;
                this.sort = options.sort;
                this.onUpdate = options.onUpdate;
                this.onFailure = options.onFailure;
                this.skipStream = options.skipStream;
                this.streamObjectType = options.streamObjectType || options.objectType;
                this.guard = options.guard || undefined;
                this.withStats = options.withStats || undefined;
                this.extraData = options.extraData || undefined;
                this.name = options.name || undefined;
                this.config = options.config || {};

                this.operations = options.operations;

                // Create a listener if the option is enabled
                if (this.skipStream !== true) {
                    var streamCfg = {
                        scope: self.scope,
                        rootId: self.root,
                        objectType: self.streamObjectType,
                        callback: function(updates) {
                            if(!self.guard || self.guard(updates)) {
                                self.update(updates, true);
                            }
                        }
                    };

                    StreamSrv.addListener(streamCfg);
                }

                /*
                Function to change the page
                */
                this.changePage = function() {
                    if (this.loadAll) {
                        this.values.length = 0;
                        var end = this.currentPage * this.pageSize;
                        var start = end - this.pageSize;
                        angular.forEach(this.allValues.slice(start, end), function(d) {
                            self.values.push(d);
                        });

                        if (angular.isFunction(this.onUpdate)) {
                            this.onUpdate(this.allValues);
                        }
                    } else {
                        this.update();
                    }
                };

                /*
                Function to compute the range of the page
                */
                this.getPage = function() {
                    if (this.loadAll) {
                        return;
                    }

                    var to = this.currentPage * this.pageSize;
                    var from = to - this.pageSize;
                    //range = start + '-' + end;

                    return _.extend(
                        {
                            from: from,
                            to: to
                        },
                        self.extraData ? {extraData: self.extraData} : {},
                        self.pageOptions ? self.pageOptions : {}
                    );
                };


                /**
                 * Prepare the filters collection to the Query service
                 *
                 * @return {type}  filters definition
                 */
                this.getFilter = function() {
                    if(!this.baseFilter && !this.filter) {
                        return;
                    }

                    var predicates = _.filter([this.baseFilter, this.filter], function(item) {
                        return !_.isEmpty(item);
                    });

                    if(predicates.length === 0) {
                        return [];
                    }

                    return predicates.length === 1 ? predicates[0] : {'_and': predicates};
                };

                /**
                 * Prepare the sort attributes to the Query service
                 *
                 * @return {type}  sort definition
                 */
                this.getSort = function() {
                    return self.sort;
                };

                /*
                Function to change the page
                */
                this.update = function(updates, forceCount) {
                    var filters = self.getFilter();

                    // Get the list
                    QuerySrv.call(this.version, this.operations, {
                        filter: filters,
                        sort: self.getSort(),
                        page: self.getPage(),
                        config: self.config,
                        withParent: false,
                        name: self.name
                    }).then(function(data) {
                        if (self.loadAll) {
                            self.allValues = data;

                            self.total = data.length;

                            self.changePage();
                        } else {
                            self.values = data;
                            if (angular.isFunction(self.onUpdate)) {
                                self.onUpdate(updates);
                            }
                        }
                    }).catch(function(err) {
                        if(self.onFailure) {
                            self.onFailure(err);
                        }
                    });

                    // get the total if not cached
                    var hash = $filter('md5')(JSON.stringify(this.filter));
                    if(forceCount || (!!!this.loadAll && this.filterHash !== hash)) {
                        this.filterHash = hash;

                        // Compute the total again
                        QuerySrv.count('v1', this.operations, {
                            filter: filters,
                            name: self.name,
                            config: self.config,
                        }).then(function(total) {
                            self.total = total;
                        });
                    }

                };

                // Call the initial load
                this.update();
            };

        });

})();
