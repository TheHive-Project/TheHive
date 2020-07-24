/**
 * Controller in add new artifact modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ObservableAnalyzeCtrl',
        function($scope, $stateParams, $uibModalInstance, selection, analyzers) {
            var self = this;

            this.selection = selection;
            this.analyzers = analyzers;

            this.$onInit = function() {
                // Get observables data ypes
                this.dataTypes = _.unique(_.pluck(this.selection, 'dataType'));

                var map = {};
                _.each(this.dataTypes, function(dt) {
                    map[dt] = [];
                });

                _.each(this.analyzers, function(a) {
                    _.each(a.dataTypeList, function(dt) {
                        if(map[dt]) {
                            map[dt].push(_.extend({}, {slected: false}, _.pick(a, 'id', 'name', 'version')));
                        }
                    });
                });

                this.analyzerMap = map;
            };

            this.selectAll = function(dataType, flag) {
                _.each(this.analyzerMap[dataType], function(a) {
                    a.selected = flag;
                });
            };

            this.run = function() {
                var operations = [];

                _.each(this.selection, function(observable) {
                    // Get selected analyzers
                    analyzers = _.filter(self.analyzerMap[observable.dataType], function(analyzer) {
                        return !!analyzer.selected;
                    });

                    // Prepare analysis
                    _.each(analyzers, function(analyzer) {
                        operations.push({
                            observableId: observable._id,
                            analyzerId: analyzer.id,
                            analyzerName: analyzer.name
                        });
                    });

                });
                
                $uibModalInstance.close(operations);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss('cancel');
            };

        }
    );

})();
