(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('PaginatedQuerySrv', function(StreamSrv, QuerySrv) {

            return function(options) {
                var self = this;

                // Internal fields
                this.values = [];
                this.allValues = [];
                this.total = 0;
                this.currentPage = 1;

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
                    console.log('Call to PaginatedQuerySrv.changePage()');
                };

                /*
                Function to change the page
                */
                this.update = function(updates) {
                    console.log('Call to PaginatedQuerySrv.update()', updates);

                    // Prepare pagination

                    // Prepare filters


                    QuerySrv.call(this.version, this.operations, {
                        filter: self.filter,
                        sort: self.sort,
                        config: {},
                        withStats: false,
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
                        // TODO nadouani: handle the total differently
                        self.total = data.length;
                    });
                };

                // Call the initial load
                this.update();
            };


        });

})();
