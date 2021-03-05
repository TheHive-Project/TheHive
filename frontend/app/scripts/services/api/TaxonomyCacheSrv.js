(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TaxonomyCacheSrv', function($http, $q, $filter, $uibModal, TagSrv, QuerySrv) {
            var self = this;

            this.cache = null;
            this.tagsCache = null;

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
                self.tagsCache = null;
            };

            this.getCache = function(name) {
                return self.cache[name];
            };

            this.getColour = function(tag) {
                return self.tagsCache[tag];
            };

            this.cacheTagColors = function(tags) {
                var fn = $filter('tagValue');

                _.each(tags, function(tag) {
                    var name = fn(tag);

                    if(!_.isEmpty(name)) {
                        self.tagsCache[name] =  tag.colour;
                    }
                });
            };

            this.all = function(reload) {
                var deferred = $q.defer();

                if (self.cache === null || reload === true) {
                    self.list()
                        .then(function(response) {
                            self.cache = {};
                            self.tagsCache = {};

                            _.each(response, function(taxonomy) {
                                self.cache[taxonomy.namespace] = taxonomy;

                                self.cacheTagColors(taxonomy.tags);
                            });
                        })
                        .then(function() {
                            return TagSrv.getFreeTags();
                        })
                        .then(function(freeTags) {
                            self.cacheTagColors(freeTags);

                            deferred.resolve(self.cache);
                        });
                } else {
                    deferred.resolve(self.cache);
                }

                return deferred.promise;
            };

            self.openTagLibrary = function() {
                var defer = $q.defer();

                var modalInstance = $uibModal.open({
                    controller: 'TaxonomySelectionModalCtrl',
                    controllerAs: '$modal',
                    animation: true,
                    templateUrl: 'views/partials/misc/taxonomy-selection.modal.html',
                    size: 'lg',
                    resolve: {
                        taxonomies: function() {
                            return self.all();
                        }
                    }
                });

                modalInstance.result
                    .then(function(selectedTags) {
                        var filterFn = $filter('tagValue'),
                            tags = [];

                        _.each(selectedTags, function(tag) {
                            tags.push({
                                text: filterFn(tag)
                            });
                        });

                        //$scope.tags = $scope.tags.concat(tags);
                        defer.resolve(tags);
                    })
                    .catch(function(err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('Tag selection', err.data, err.status);
                        }
                        defer.rejeect(err);
                    });

                return defer.promise;
            };
        });
})();
