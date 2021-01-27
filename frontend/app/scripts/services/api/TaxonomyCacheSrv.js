(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TaxonomyCacheSrv', function($http, $q, QuerySrv) {
            var self = this;

            this.cache = null;

            this.list = function() {
                return QuerySrv.call('v1', [
                    { _name: 'listTaxonomy' }
                ], {
                    name:'list-taxonomies'
                }, {
                    name: 'filter',
                    _field: 'enabled',
                    _value: true
                });
            };

            this.clearCache = function() {
                self.cache = null;
            };

            this.getCache = function(name) {
                return self.cache[name];
            };

            this.all = function(reload) {
                var deferred = $q.defer();

                if (self.cache === null || reload === true) {
                    self.list()
                        .then(function(response) {
                            self.cache = {};

                            _.each(response, function(taxonomy) {
                                self.cache[taxonomy.namespace] = taxonomy;
                            });

                            deferred.resolve(self.cache);
                        });
                } else {
                    deferred.resolve(self.cache);
                }

                return deferred.promise;
            };
        });
})();
