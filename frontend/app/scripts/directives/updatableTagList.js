(function() {
    'use strict';
    angular.module('theHiveDirectives')
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
                            controller: 'TaxonomySelectionModalCtrl',
                            controllerAs: '$modal',
                            animation: true,
                            templateUrl: 'views/partials/misc/taxonomy-selection.modal.html',
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
