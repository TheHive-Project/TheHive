/**
 * Controller for About TheHive modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AddProcedureModalCtrl', function($rootScope, $scope, $uibModalInstance, NotificationSrv, ProcedureSrv, AttackPatternSrv, caseId) {
            var self = this;

            this.caseId = caseId;

            this.close = function() {
                $uibModalInstance.close();
            };

            this.cancel = function() {
                $rootScope.markdownEditorObjects.procedure.hidePreview();

                $uibModalInstance.dismiss();
            };

            this.addProcedure = function() {
                self.state.loading = true;

                ProcedureSrv.create({
                    caseId: self.caseId,
                    tactic: self.procedure.tactic,
                    description: self.procedure.description,
                    patternId: self.procedure.patternId,
                    occurDate: self.procedure.occurDate
                }).then(function(/*response*/) {
                    self.state.loading = false;
                    $uibModalInstance.close();
                    NotificationSrv.log('Tactic, Technique and Procedure added successfully', 'success');
                }).catch(function(err) {
                    NotificationSrv.error('Add TTP', err.data, err.status);
                    self.state.loading = false;
                });
            };

            this.showTechniques = function() {
                AttackPatternSrv.getByTactic(self.procedure.tactic)
                    .then(function(techniques) {
                        self.state.techniques = techniques;

                        self.procedure.patternId = null;
                    });
            };

            this.$onInit = function() {
                this.markdownEditorOptions = {
                    iconlibrary: 'fa',
                    addExtraButtons: true,
                    resize: 'vertical'
                };

                this.procedure = {
                    tactic: null,
                    description: null,
                    patternId: null
                };

                this.tactics = AttackPatternSrv.tactics;

                this.state = {
                    loading: false,
                    selectedTactic: null,
                    techniques: null
                };

                $scope.$broadcast('beforeProcedureModalShow');
            };
        }
    );
})();
