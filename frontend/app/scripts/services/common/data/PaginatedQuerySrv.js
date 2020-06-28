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
                this.filter = options.filter;
                this.sort = options.sort;
                this.onUpdate = options.onUpdate;
                this.skipStream = options.skipStream;
                this.streamObjectType = options.skipStream || options.objectType;
                this.guard = options.guard || undefined;
                this.withStats = options.withStats || undefined;
                this.extraData = options.extraData || undefined;

                this.operations = options.operations;

                // Create a listener if the option is enabled
                if (this.skipStream !== true) {
                    var streamCfg = {
                        scope: self.scope,
                        rootId: self.root,
                        objectType: self.streamObjectType,
                        callback: function(updates) {
                            if(!self.guard || self.guard(updates)) {
                                self.update(updates);
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
                            this.onUpdate();
                        }
                    } else {
                        this.update();
                    }
                };

                /*
                Function to compute the range of the page
                */
                this.getPage = function() {
                    var to = this.currentPage * this.pageSize;
                    var from = to - this.pageSize;
                    //range = start + '-' + end;

                    return _.extend({
                        from: from,
                        to: to
                    }, self.extraData ? {extraData: self.extraData} : {});
                };

                /*
                Function to change the page
                */
                this.update = function(updates) {
                    // Get the list
                    QuerySrv.call(this.version, this.operations, {
                        filter: self.filter,
                        sort: self.sort,
                        page: self.getPage(),
                        config: {},
                        withParent: false
                    }).then(function(data) {
                        if (self.loadAll) {
                            self.allValues = data;
                            self.changePage();
                        } else {
                            self.values = data;
                            if (angular.isFunction(self.onUpdate)) {
                                self.onUpdate(updates);
                            }
                        }
                    });

                    // get the total if not cached
                    var hash = $filter('md5')(JSON.stringify(this.filter));
                    if(this.filterHash !== hash) {
                        this.filterHash = hash;

                        // Compute the total again
                        QuerySrv.count('v1', this.operations, {
                            filter: self.filter,
                            config: {}
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
