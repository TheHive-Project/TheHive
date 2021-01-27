(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .controller('UpdatableTagListModalCtrl', function($uibModalInstance, taxonomies) {
            var self = this;

            this.taxonomies = angular.copy(taxonomies);

            this.formData = {
                selectedTaxonomy: null,
                selectedTags: null
            };

            this.addSelectedTags = function() {
                if (!self.formData.selectedTaxonomy) {
                    return;
                }

                var selection = _.filter(self.formData.selectedTaxonomy.tags, function(tag) {
                    return tag.selected;
                });

                if (selection.length === 0) {
                    return;
                }

                $uibModalInstance.close(selection);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        })
        .directive('updatableTagList', function(UtilsSrv, $uibModal, $filter, NotificationSrv, TaxonomyCacheSrv) {
            return {
                restrict: 'E',
                link: UtilsSrv.updatableLink,
                templateUrl: 'views/directives/updatable-tag-list.html',
                scope: {
                    value: '=?',
                    onUpdate: '&',
                    active: '=?',
                    source: '=',
                    clearable: '<?'
                },
                controllerAs: '$cmp',
                controller: function($scope) {
                    this.state = {
                        type: null,
                    };

                    this.fromLibrary = function() {
                        this.state.type = 'library';

                        var modalInstance = $uibModal.open({
                            controller: 'UpdatableTagListModalCtrl',
                            controllerAs: '$modal',
                            animation: true,
                            templateUrl: 'views/directives/updatable-tag-list-modal.html',
                            size: 'lg',
                            resolve: {
                                taxonomies: function() {
                                    return TaxonomyCacheSrv.all();
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

                                $scope.value = $scope.value.concat(tags);
                            })
                            .catch(function(err) {
                                if (err && !_.isString(err)) {
                                    NotificationSrv.error('Tag selection', err.data, err.status);
                                }
                            });
                    };

                }
            };
        });
})();
